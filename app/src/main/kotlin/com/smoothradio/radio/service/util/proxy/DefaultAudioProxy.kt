package com.smoothradio.radio.service.util.proxy

import com.smoothradio.radio.core.util.PlaybackConstants
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A local HTTP proxy implementation that downloads a live stream to a rolling two-part buffer.
 */
class DefaultAudioProxy(
    cacheDir: File,
    ioDispatcher: CoroutineDispatcher,
    okHttpClient: OkHttpClient
) : AudioProxy {
    private val internalOkHttpClient = okHttpClient.newBuilder()
        .apply {
            interceptors().clear()
            networkInterceptors().clear()
        }
        .build()

    private val sessionJob = SupervisorJob()
    private val scope = CoroutineScope(ioDispatcher + sessionJob)
    private val isRunning = AtomicBoolean(false)
    private val dataSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val stateLock = ReentrantLock()
    private val dataCondition = stateLock.newCondition()

    private val cache = RollingDiskCache(cacheDir, stateLock, dataCondition)
    private val httpServer = LocalHttpServer(
        scope, ioDispatcher, cache, dataSignal,
        isRunning = { isRunning.get() },
        sessionTag = { sessionTag },
        getMimeType = { remoteMimeType },
        getBitrate = { remoteBitrate },
        currentUrl = { currentUrl }
    )

    @Volatile
    override var estimatedBytesPerMs: Double = PlaybackConstants.INITIAL_BITRATE_ESTIMATION

    @Volatile
    private var sessionStartTime: Long = 0L

    @Volatile
    private var currentUrl: String? = null

    @Volatile
    override var sessionTag: String = ""
        private set

    override val part1File: File? get() = cache.part1File
    override val part2File: File? get() = cache.part2File

    private val _proxyState = MutableStateFlow<ProxyState>(ProxyState.Idle)
    override val proxyState: StateFlow<ProxyState> = _proxyState.asStateFlow()

    @Volatile
    override var terminalError: Int = 0
        private set

    private val remoteMimeType: String? get() = (proxyState.value as? ProxyState.Streaming)?.mimeType
    private val remoteBitrate: String? get() = (proxyState.value as? ProxyState.Streaming)?.bitrate

    @Volatile
    var detectedBitrateKbps: Double? = null
        private set

    override val totalBytesDropped: Long get() = cache.totalBytesDropped
    override val totalBytesWritten: Long get() = cache.totalBytesWritten
    override val totalBytesReceived: Long get() = cache.totalBytesReceived

    @Volatile
    override var lastReadPosition = 0L
        private set

    override fun isStartedFor(url: String): Boolean {
        return isRunning.get() && currentUrl == url
    }

    override fun updateLastReadPosition(pos: Long) {
        lastReadPosition = pos
    }

    override fun start(streamUrl: String) {
        val tagAtStart = UUID.randomUUID().toString().take(8)

        terminalError = 0
        stop()

        stateLock.withLock {
            currentUrl = streamUrl
            sessionTag = tagAtStart
            _proxyState.value = ProxyState.Connecting
            estimatedBytesPerMs = PlaybackConstants.INITIAL_BITRATE_ESTIMATION
            sessionStartTime = System.currentTimeMillis()
            cache.reset(tagAtStart)
            isRunning.set(true)
        }

        httpServer.start(tagAtStart)

        scope.launch {
            val isHls = streamUrl.contains(".m3u8") || streamUrl.contains("playlist")
            val downloader: StreamDownloader = if (isHls) {
                HlsDownloader(
                    internalOkHttpClient, cache, scope,
                    isRunning = { isRunning.get() },
                    sessionTag = { sessionTag },
                    onBitrateDetected = { detectedBitrateKbps = it },
                    onTerminalError = { handleError(it) }
                )
            } else {
                ProgressiveDownloader(
                    internalOkHttpClient, cache,
                    isRunning = { isRunning.get() },
                    sessionTag = { sessionTag },
                    onStateUpdate = { mime, br ->
                        _proxyState.value = ProxyState.Streaming(mime, br)
                    },
                    onTerminalError = { handleError(it) },
                    onBitrateDetected = { detectedBitrateKbps = it }
                )
            }
            downloader.download(streamUrl, tagAtStart)
        }
    }

    private fun handleError(error: Int) {
        terminalError = error
        stop()
    }

    override fun getMetadataForOffset(offset: Long): String? = cache.getMetadataForOffset(offset)

    override fun readData(
        tag: String,
        position: Long,
        buffer: ByteArray,
        offset: Int,
        length: Int
    ): Int {
        return cache.readData(
            tag, position, buffer, offset, length,
            isRunning = { isRunning.get() },
            terminalError = { terminalError }
        ).also { read ->
            if (read > 0) {
                lastReadPosition = position + read
                dataSignal.tryEmit(Unit)
            }
        }
    }

    override fun stop() {
        isRunning.set(false)
        sessionJob.cancelChildren()

        stateLock.withLock {
            cache.cleanup()
            dataCondition.signalAll()
            sessionTag = ""
        }

        sessionStartTime = 0L
        dataSignal.tryEmit(Unit)
        _proxyState.value = ProxyState.Idle
        httpServer.stop()
    }

    override fun updateBitrateEstimation() {
        if (sessionStartTime == 0L) return

        val bytes = totalBytesWritten
        val elapsed = System.currentTimeMillis() - sessionStartTime

        if (elapsed > PlaybackConstants.BITRATE_CALIBRATION_THRESHOLD_MS && bytes > 0) {
            val realTimeBitrate = (bytes.toDouble() / elapsed.toDouble())
                .coerceIn(
                    PlaybackConstants.MIN_BITRATE_BYTES_PER_MS,
                    PlaybackConstants.MAX_BITRATE_BYTES_PER_MS
                )

            val manifestBitrate = detectedBitrateKbps?.let { it / PlaybackConstants.BITS_PER_BYTE }

            val oldEstimation = estimatedBytesPerMs
            val targetEstimation = if (manifestBitrate != null) {
                (realTimeBitrate * PlaybackConstants.BITRATE_REALITY_WEIGHT) +
                        (manifestBitrate * PlaybackConstants.BITRATE_HINT_WEIGHT)
            } else {
                realTimeBitrate
            }

            estimatedBytesPerMs = (oldEstimation * 0.9) + (targetEstimation * 0.1)
        } else if (detectedBitrateKbps != null) {
            estimatedBytesPerMs = detectedBitrateKbps!! / PlaybackConstants.BITS_PER_BYTE
        }
    }
}
