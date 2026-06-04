@file:OptIn(UnstableApi::class)

package com.smoothradio.radio.service.util

import android.content.Context
import android.net.Uri
import timber.log.Timber
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.annotation.OptIn
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thrown when the requested stream position has already been purged from the proxy's rolling buffer.
 */
class BufferEvictedException(position: Long) : IOException("Stream position $position has been purged from history")

/**
 * A wrapper DataSource that handles our custom "proxy://" scheme while delegating others
 * to the standard DefaultDataSource.
 */
class SmoothDataSource(
    proxy: LocalAudioProxy,
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
            ProxyDataSource.SCHEME -> proxyDataSource
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
    private val proxy: LocalAudioProxy
) : BaseDataSource(/* isNetwork= */ false) {

    companion object {
        const val SCHEME = "proxy"
    }

    class Factory(
        private val context: Context,
        private val proxy: LocalAudioProxy,
        private val baseFactory: DataSource.Factory
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            val defaultDataSource = DefaultDataSource.Factory(context, baseFactory).createDataSource()
            return SmoothDataSource(proxy, defaultDataSource)
        }
    }

    private var dataSpec: DataSpec? = null
    private var position: Long = 0
    private val isOpened = AtomicBoolean(false)

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        
        // Parse custom byteOffset from URI query parameter if present
        val uri = dataSpec.uri
        val queryOffset = uri.getQueryParameter("byteOffset")?.toLongOrNull()
        
        // Use the query offset for seeking, falling back to ExoPlayer's position
        this.position = queryOffset ?: dataSpec.position
        
        Timber.d("open() called: uri=%s, requestedPos=%d, effectivePos=%d", uri, dataSpec.position, position)

        transferInitializing(dataSpec)
        isOpened.set(true)
        transferStarted(dataSpec)
        
        // Return C.LENGTH_UNSET to indicate an unknown/infinite stream length.
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (!isOpened.get()) return C.RESULT_END_OF_INPUT

        return when (val bytesRead = proxy.readData(position, buffer, offset, length)) {
            -1 -> C.RESULT_END_OF_INPUT // EOF
            -2 -> throw BufferEvictedException(position)
            else -> {
                position += bytesRead
                bytesTransferred(bytesRead)
                bytesRead
            }
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
