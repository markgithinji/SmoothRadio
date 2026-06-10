package com.smoothradio.radio.service.util.proxy

import android.util.Log
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
import kotlin.math.abs

/**
 * A local HTTP proxy that downloads a live stream to a rolling two-part buffer.
 * Provides a "Time Machine" seeking experience while strictly limiting disk usage.
 */
class LocalAudioProxy(
    cacheDir: File,
    ioDispatcher: CoroutineDispatcher,
    okHttpClient: OkHttpClient
) {
    private val internalOkHttpClient = okHttpClient.newBuilder()
        .apply {
            interceptors().clear()
            networkInterceptors().clear()
        }
        .build()

    private val sessionJob = SupervisorJob()
    private val scope = CoroutineScope(ioDispatcher + sessionJob)
    private val isRunning = AtomicBoolean(false)

    // Signal for handleClient to wake up when new data is available
    private val dataSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    
    // Lock for state and metadata synchronization
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
    var estimatedBytesPerMs: Double = PlaybackConstants.INITIAL_BITRATE_ESTIMATION

    @Volatile
    private var sessionStartTime: Long = 0L

    @Volatile
    private var currentUrl: String? = null
    
    @Volatile
    var sessionTag: String = ""
        private set
    
    private val _proxyState = MutableStateFlow<ProxyState>(ProxyState.Idle)
    val proxyState: StateFlow<ProxyState> = _proxyState.asStateFlow()
    
    @Volatile
    var terminalError: Int = 0
        private set

    val remoteMimeType: String? get() = (proxyState.value as? ProxyState.Streaming)?.mimeType
    val remoteBitrate: String? get() = (proxyState.value as? ProxyState.Streaming)?.bitrate
    
    @Volatile
    var detectedBitrateKbps: Double? = null
        private set

    val totalBytesDropped: Long get() = cache.totalBytesDropped
    val totalBytesWritten: Long get() = cache.totalBytesWritten
    val totalBytesReceived: Long get() = cache.totalBytesReceived
    
    @Volatile
    var lastReadPosition = 0L
        private set

    fun isStartedFor(url: String): Boolean {
        return isRunning.get() && currentUrl == url
    }

    fun updateLastReadPosition(pos: Long) {
        if (abs(pos - lastReadPosition) > 1024 * 1024) {
            Log.d("SmoothSeek", "LocalAudioProxy: lastReadPosition jumped from $lastReadPosition to $pos")
        }
        lastReadPosition = pos
    }

    fun start(streamUrl: String) {
        val tagAtStart = UUID.randomUUID().toString().take(8)
        Log.d("SmoothSeek", "LocalAudioProxy.start: station=$streamUrl, tag=$tagAtStart")
        
        // Reset terminal error before starting new session to prevent race with old reads
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
                    onStateUpdate = { mime, br -> _proxyState.value = ProxyState.Streaming(mime, br) },
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

    fun getMetadataForOffset(offset: Long): String? = cache.getMetadataForOffset(offset)

    fun readData(tag: String, position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        return cache.readData(tag, position, buffer, offset, length, 
            isRunning = { isRunning.get() }, 
            terminalError = { terminalError }
        ).also { read ->
            if (read > 0) {
                lastReadPosition = position + read
                dataSignal.tryEmit(Unit)
            }
        }
    }

    fun stop() {
        Log.d("SmoothSeek", "LocalAudioProxy.stop (wasRunning=${isRunning.get()}, tag=$sessionTag)")
        isRunning.set(false)
        
        // Cancel all coroutines under sessionJob (download, proxy, and client handlers)
        sessionJob.cancelChildren()
        
        stateLock.withLock {
            cache.cleanup()
            dataCondition.signalAll()
            // Clear tag while holding lock
            sessionTag = ""
        }
        
        sessionStartTime = 0L
        dataSignal.tryEmit(Unit)
        _proxyState.value = ProxyState.Idle
        httpServer.stop()
    }

    fun updateBitrateEstimation() {
        if (sessionStartTime == 0L) return
        
        val bytes = totalBytesWritten
        val elapsed = System.currentTimeMillis() - sessionStartTime
        
        // Start trusting reality after calibration period to correct for lying manifests
        if (elapsed > PlaybackConstants.BITRATE_CALIBRATION_THRESHOLD_MS && bytes > 0) {
            val realTimeBitrate = (bytes.toDouble() / elapsed.toDouble())
                .coerceIn(PlaybackConstants.MIN_BITRATE_BYTES_PER_MS, PlaybackConstants.MAX_BITRATE_BYTES_PER_MS)
            
            val manifestBitrate = detectedBitrateKbps?.let { it / PlaybackConstants.BITS_PER_BYTE }
            
            val oldEstimation = estimatedBytesPerMs
            val targetEstimation = if (manifestBitrate != null) {
                // Blend reality with manifest hint
                (realTimeBitrate * PlaybackConstants.BITRATE_REALITY_WEIGHT) + 
                (manifestBitrate * PlaybackConstants.BITRATE_HINT_WEIGHT)
            } else {
                realTimeBitrate
            }

            // DAMPING: Only move 10% towards the new target each check to prevent duration jitter
            estimatedBytesPerMs = (oldEstimation * 0.9) + (targetEstimation * 0.1)
            
            if (abs(oldEstimation - estimatedBytesPerMs) > 0.5) {
                Log.d("SmoothSeek", "LocalAudioProxy: Bitrate estimation updated: $estimatedBytesPerMs")
            }
        } else if (detectedBitrateKbps != null) {
            estimatedBytesPerMs = detectedBitrateKbps!! / PlaybackConstants.BITS_PER_BYTE
        }
    }
}
