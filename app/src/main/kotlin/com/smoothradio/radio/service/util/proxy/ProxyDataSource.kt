@file:OptIn(UnstableApi::class)

package com.smoothradio.radio.service.util.proxy

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import com.smoothradio.radio.core.util.PlaybackConstants
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A wrapper DataSource that handles our custom "proxy://" scheme while delegating others
 * to the standard DefaultDataSource.
 */
class SmoothDataSource(
    proxy: AudioProxy,
    private val baseDataSource: DataSource
) : DataSource {

    private val proxyDataSource = ProxyDataSource(proxy)
    private var activeDataSource: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        baseDataSource.addTransferListener(transferListener)
        proxyDataSource.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        activeDataSource = when (dataSpec.uri.scheme) {
            PlaybackConstants.PROXY_SCHEME -> proxyDataSource
            else -> baseDataSource
        }
        return activeDataSource!!.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return activeDataSource?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT
    }

    override fun getUri(): Uri? = activeDataSource?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        activeDataSource?.responseHeaders ?: emptyMap()

    override fun close() {
        activeDataSource?.close()
        activeDataSource = null
    }
}

/**
 * A custom Media3 DataSource that reads directly from the LocalAudioProxy rolling buffer.
 * Bypasses HTTP overhead and provides deterministic stream behavior.
 */
class ProxyDataSource(
    private val proxy: AudioProxy
) : BaseDataSource(/* isNetwork= */ false) {

    class Factory(
        private val context: Context,
        private val proxy: AudioProxy,
        private val baseFactory: DataSource.Factory
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            val defaultDataSource =
                DefaultDataSource.Factory(context, baseFactory).createDataSource()
            return SmoothDataSource(proxy, defaultDataSource)
        }
    }

    private var dataSpec: DataSpec? = null
    private var position: Long = 0
    private var sessionTag: String? = null
    private val isOpened = AtomicBoolean(false)

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec

        // Capture the session tag that is active WHEN this data source is opened.
        // This ensures this specific instance only reads data for the station it was created for.
        this.sessionTag = proxy.sessionTag

        // Parse custom byteOffset from URI query parameter if present
        val uri = dataSpec.uri
        val queryOffset =
            uri.getQueryParameter(PlaybackConstants.PROXY_PARAM_BYTE_OFFSET)?.toLongOrNull()

        // Use the query offset for seeking, falling back to ExoPlayer's position
        this.position = queryOffset ?: dataSpec.position

        transferInitializing(dataSpec)
        isOpened.set(true)
        transferStarted(dataSpec)

        // Return C.LENGTH_UNSET to indicate an unknown/infinite stream length.
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (!isOpened.get()) return C.RESULT_END_OF_INPUT

        val tag = sessionTag ?: return C.RESULT_END_OF_INPUT

        // GHOST ERROR PREVENTION: If the session tag has changed, this DataSource belongs to an
        // abandoned station. Instead of returning EOF or an Exception (which triggers
        // UnrecognizedInputFormatException during sniffing), we block the loader thread
        // until ExoPlayer naturally closes this DataSource.
        // CRITICAL: We only block if there is NO terminal error. If the station failed,
        // we want to report that failure immediately.
        if (tag != proxy.sessionTag) {
            handleTerminalError(proxy.terminalError)

            while (isOpened.get() && tag != proxy.sessionTag) {
                handleTerminalError(proxy.terminalError)
                try {
                    Thread.sleep(100)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return C.RESULT_END_OF_INPUT
                }
            }
            return C.RESULT_END_OF_INPUT
        }

        return try {
            val bytesRead = proxy.readData(tag, position, buffer, offset, length)

            when (bytesRead) {
                -1 -> {
                    // If readData returns -1, it might be due to a session change that occurred DURING the read.
                    if (tag != proxy.sessionTag) {
                        handleTerminalError(proxy.terminalError)

                        while (isOpened.get() && tag != proxy.sessionTag) {
                            handleTerminalError(proxy.terminalError)
                            try {
                                Thread.sleep(100)
                            } catch (e: InterruptedException) {
                                Thread.currentThread().interrupt()
                                break
                            }
                        }
                        return C.RESULT_END_OF_INPUT
                    }
                    C.RESULT_END_OF_INPUT
                }

                -2 -> {
                    // Buffer evicted - we need to seek to the new start position
                    // The new valid start position is at totalBytesDropped (byte offset 0 in current buffer)
                    // We don't have estimatedBytesPerMs here, so we'll throw with the byte offset
                    // and let StreamService handle the conversion
                    val newValidStartBytes = proxy.totalBytesDropped

                    // Pass the byte offset - StreamService will convert to milliseconds
                    throw BufferEvictedException(position, newValidStartBytes)
                }

                -3 -> {
                    // Terminal error from proxy
                    handleTerminalError(proxy.terminalError)
                    C.RESULT_END_OF_INPUT
                }

                else -> {
                    // Successfully read data
                    if (bytesRead > 0) {
                        position += bytesRead
                        proxy.updateLastReadPosition(position)
                        bytesTransferred(bytesRead)
                    }
                    bytesRead
                }
            }
        } catch (e: BufferEvictedException) {
            // Re-throw to be handled in onPlayerError
            throw e
        } catch (e: Exception) {
            handleTerminalError(PlaybackConstants.ERROR_CACHE_ERROR)
            C.RESULT_END_OF_INPUT
        }
    }

    private fun handleTerminalError(errorCode: Int) {
        when (errorCode) {
            PlaybackConstants.ERROR_UNREACHABLE -> throw StationUnreachableException(getUri()?.toString())
            PlaybackConstants.ERROR_EMPTY_STREAM -> throw EmptyStreamException()
            PlaybackConstants.ERROR_CACHE_ERROR -> throw ProxyCacheException("Local buffer read error")
        }
    }

    override fun getUri(): Uri? = dataSpec?.uri

    override fun close() {
        if (isOpened.compareAndSet(true, false)) {
            transferEnded()
        }
        dataSpec = null
    }
}
