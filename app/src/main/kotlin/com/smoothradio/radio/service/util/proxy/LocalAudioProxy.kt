package com.smoothradio.radio.service.util.proxy

import android.util.Log
import com.smoothradio.radio.core.util.PlaybackConstants
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.ServerSocket
import java.net.Socket
import java.util.TreeMap
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

/**
 * A local HTTP proxy that downloads a live stream to a rolling two-part buffer.
 * Provides a "Time Machine" seeking experience while strictly limiting disk usage.
 */
class LocalAudioProxy(
    private val cacheDir: File,
    private val ioDispatcher: CoroutineDispatcher,
    okHttpClient: OkHttpClient
) {

    private val internalOkHttpClient = okHttpClient.newBuilder()
        .apply {
            interceptors().clear()
            networkInterceptors().clear()
        }
        .build()

    private var serverSocket: ServerSocket? = null
    private val sessionJob = SupervisorJob()
    private val scope = CoroutineScope(ioDispatcher + sessionJob)
    private val isRunning = AtomicBoolean(false)

    // Signal for handleClient to wake up when new data is available
    private val dataSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    
    // Lock for state and metadata synchronization
    private val stateLock = ReentrantLock()
    private val dataCondition = stateLock.newCondition()

    @Volatile
    var estimatedBytesPerMs: Double = PlaybackConstants.INITIAL_BITRATE_ESTIMATION

    @Volatile
    private var sessionStartTime: Long = 0L

    @Volatile
    private var currentUrl: String? = null
    
    @Volatile
    var sessionTag: String = ""
        private set
    
    @Volatile
    private var proxyState: ProxyState = ProxyState.Idle
    
    @Volatile
    var terminalError: Int = 0
        private set

    val remoteMimeType: String? get() = (proxyState as? ProxyState.Streaming)?.mimeType
    val remoteBitrate: String? get() = (proxyState as? ProxyState.Streaming)?.bitrate
    
    @Volatile
    var detectedBitrateKbps: Double? = null
        private set

    private val metadataMap = TreeMap<Long, String>()
    private val memoryBuffer = FastMemoryBuffer(INITIAL_BURST_SIZE)

    var part1File: File? = null
        private set
    var part2File: File? = null
        private set
    
    @Volatile
    var totalBytesDropped = 0L
        private set
    @Volatile
    var totalBytesWritten = 0L
        private set
    @Volatile
    var totalBytesReceived = 0L
        private set
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
            proxyState = ProxyState.Connecting
            estimatedBytesPerMs = PlaybackConstants.INITIAL_BITRATE_ESTIMATION
            sessionStartTime = System.currentTimeMillis()
            cleanupLegacyFiles()

            part1File = File(cacheDir, "proxy_${sessionTag}_p1.mp3").apply { createNewFile() }
            part2File = File(cacheDir, "proxy_${sessionTag}_p2.mp3").apply { createNewFile() }
            totalBytesDropped = 0L
            totalBytesWritten = 0L
            totalBytesReceived = 0L
            metadataMap.clear()
            isRunning.set(true)
        }

        serverSocket = ServerSocket(0)

        scope.launch {
            val isHls = streamUrl.contains(".m3u8") || streamUrl.contains("playlist")
            if (isHls) downloadHlsStream(streamUrl, tagAtStart) else downloadProgressiveStream(
                streamUrl,
                tagAtStart
            )
        }

        scope.launch {
            while (isRunning.get() && sessionTag == tagAtStart) {
                runCatching {
                    val client = serverSocket?.accept() ?: return@launch
                    handleClient(client, tagAtStart)
                }
            }
        }
    }

    private suspend fun downloadProgressiveStream(streamUrl: String, tag: String) =
        withContext(ioDispatcher) {
            var retryCount = 0
            var currentDelay = INITIAL_RETRY_DELAY_MS
            val maxRetries = 2

            while (isRunning.get() && sessionTag == tag && retryCount < maxRetries) {
                try {
                    val timeout = if (retryCount > 0) RETRY_READ_TIMEOUT_SEC else DEFAULT_READ_TIMEOUT_SEC
                    var useMetadata = true
                    var response = executeStreamRequest(streamUrl, true, timeout)

                    if (response != null && (response.code == 401 || response.code == 403)) {
                        response.close()
                        useMetadata = false
                        response = executeStreamRequest(streamUrl, false, timeout)
                    }

                    response?.use { res ->
                        if (!res.isSuccessful) throw Exception("HTTP ${res.code}")
                        retryCount = 0
                        currentDelay = SUCCESS_RETRY_DELAY_MS
                        
                        proxyState = ProxyState.Streaming(res.header("Content-Type"), res.header("icy-br"))
                        val inputStream = res.body.byteStream()
                        val metaint = if (useMetadata) res.header("icy-metaint")?.toIntOrNull() ?: -1 else -1

                        if (metaint > 0) {
                            var bytesUntilMetadata = metaint
                            while (isRunning.get() && sessionTag == tag) {
                                if (bytesUntilMetadata > 0) {
                                    val buf = ByteArray(minOf(bytesUntilMetadata, 8192))
                                    val read = inputStream.read(buf)
                                    if (read == -1) break
                                    appendData(tag, buf, read)
                                    bytesUntilMetadata -= read
                                } else {
                                    val n = inputStream.read()
                                    if (n == -1) break
                                    if (n > 0) {
                                        val metaLen = n * 16
                                        val metaBuf = ByteArray(metaLen)
                                        var metaRead = 0
                                        while (metaRead < metaLen) {
                                            val r = inputStream.read(metaBuf, metaRead, metaLen - metaRead)
                                            if (r == -1) break
                                            metaRead += r
                                        }
                                        val metadata = String(metaBuf, 0, metaRead, Charsets.UTF_8)
                                        parseIcyMetadata(metadata)?.let { title ->
                                            stateLock.withLock {
                                                if (metadataMap.lastEntry()?.value != title) {
                                                    Log.d("SmoothMetadata", "LocalAudioProxy: Storing metadata at byte $totalBytesWritten: $title")
                                                    metadataMap[totalBytesWritten] = title
                                                }
                                            }
                                        }
                                    }
                                    bytesUntilMetadata = metaint
                                }
                            }
                        } else {
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (isRunning.get() && sessionTag == tag) {
                                bytesRead = inputStream.read(buffer)
                                if (bytesRead == -1) break
                                appendData(tag, buffer, bytesRead)
                            }
                        }
                    }
                    if (isRunning.get() && sessionTag == tag) throw Exception("Connection lost")
                } catch (e: Exception) {
                    if (!isRunning.get() || sessionTag != tag) break
                    retryCount++
                    delay(currentDelay.milliseconds)
                    currentDelay = (currentDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                }
            }

            if (retryCount >= maxRetries) {
                terminalError = PlaybackConstants.ERROR_UNREACHABLE
                stop()
            }
        }

    private fun executeStreamRequest(url: String, requestMetadata: Boolean, timeoutSeconds: Int): Response? {
        val client = internalOkHttpClient.newBuilder()
            .readTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .connectTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .build()
        val requestBuilder = Request.Builder().url(url)
            .addHeader("User-Agent", "ExoPlayer/2.18.5")
            .addHeader("Accept", "*/*")
            .addHeader("Connection", "keep-alive")
        if (requestMetadata) requestBuilder.addHeader("Icy-MetaData", "1")
        return try { client.newCall(requestBuilder.build()).execute() } catch (e: Exception) { null }
    }

    private fun parseIcyMetadata(metadata: String): String? {
        val match = Regex("StreamTitle='(.*?)';", RegexOption.DOT_MATCHES_ALL).find(metadata)
        val rawTitle = match?.groupValues?.get(1) ?: return null
        if (rawTitle.startsWith("<?xml") || rawTitle.contains("<LogEvent")) {
            try {
                val title = Regex("Title=\"(.*?)\"").find(rawTitle)?.groupValues?.get(1)
                val artist = Regex("Artist1=\"(.*?)\"").find(rawTitle)?.groupValues?.get(1)
                if (title != null && artist != null) return "$title - $artist"
                if (title != null) return title
            } catch (e: Exception) {}
        }
        return rawTitle
    }

    private suspend fun downloadHlsStream(playlistUrl: String, tag: String): Unit = withContext(ioDispatcher) {
        val downloadedSegments = mutableSetOf<String>()
        val baseUrl = playlistUrl.substring(0, playlistUrl.lastIndexOf("/") + 1)
        var retryCount = 0
        var currentDelay = HLS_SEGMENT_DOWNLOAD_DELAY_MS
        val maxRetries = 2

        while (isRunning.get() && sessionTag == tag && retryCount < maxRetries) {
            try {
                Log.d("SmoothSeek", "LocalAudioProxy.downloadHlsStream: fetching playlist from $playlistUrl (tag=$tag)")
                val request = Request.Builder().url(playlistUrl).addHeader("User-Agent", "Mozilla/5.0").build()
                val playlistText = internalOkHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                    response.body.string()
                }
                retryCount = 0
                currentDelay = SUCCESS_RETRY_DELAY_MS

                if (playlistText.isEmpty()) { delay(HLS_PLAYLIST_RETRY_DELAY_MS.milliseconds); continue }

                val lines = playlistText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (playlistText.contains("#EXT-X-STREAM-INF")) {
                    val variantLines = lines.mapIndexedNotNull { index, line ->
                        if (line.startsWith("#EXT-X-STREAM-INF")) {
                            val url = lines.getOrNull(index + 1)
                            if (url != null && !url.startsWith("#")) line to url else null
                        } else null
                    }
                    val bestVariant = variantLines.mapNotNull { (info, url) ->
                        val bandwidth = Regex("BANDWIDTH=(\\d+)").find(info)?.groupValues?.get(1)?.toLongOrNull()
                        if (bandwidth != null) Triple(bandwidth, info, url) else null
                    }.maxByOrNull { it.first }

                    if (bestVariant != null) {
                        detectedBitrateKbps = bestVariant.first.toDouble() / 1000.0
                        val fullUrl = if (bestVariant.third.startsWith("http")) bestVariant.third else baseUrl + bestVariant.third
                        downloadHlsStream(fullUrl, tag)
                        return@withContext
                    }
                }

                val allSegments = lines.filter { !it.startsWith("#") }
                val newSegments = allSegments.filter { !downloadedSegments.contains(it) }

                val deferreds = newSegments.take(MAX_PARALLEL_DOWNLOADS).map { segmentPath ->
                    val segmentUrl = if (segmentPath.startsWith("http")) segmentPath else baseUrl + segmentPath
                    scope.async {
                        runCatching {
                            val segRequest = Request.Builder().url(segmentUrl).addHeader("User-Agent", "Mozilla/5.0").build()
                            internalOkHttpClient.newCall(segRequest).execute().use { response ->
                                if (!response.isSuccessful) return@use byteArrayOf()
                                val out = ByteArrayOutputStream()
                                val inputStream = response.body.byteStream()
                                val buffer = ByteArray(8192)
                                var read: Int
                                while (isRunning.get() && sessionTag == tag) {
                                    read = inputStream.read(buffer)
                                    if (read == -1) break
                                    out.write(buffer, 0, read)
                                    stateLock.withLock { totalBytesReceived += read }
                                }
                                out.toByteArray()
                            }
                        }.getOrDefault(byteArrayOf())
                    }
                }

                val segmentDataList = deferreds.awaitAll()
                newSegments.take(MAX_PARALLEL_DOWNLOADS).forEachIndexed { index, segmentPath ->
                    if (!isRunning.get() || sessionTag != tag) return@forEachIndexed
                    val data = segmentDataList[index]
                    if (data.isNotEmpty()) {
                        var headerOffset = 0
                        // SAFER ID3 STRIPPING: Only strip if we find a valid ID3 tag at the very beginning.
                        // Large ID3 tags at the start of HLS segments confuse the progressive sniffer.
                        if (data.size > 10 && data[0] == 'I'.code.toByte() && data[1] == 'D'.code.toByte() && data[2] == '3'.code.toByte()) {
                            val id3Size = ((data[6].toInt() and 0x7F) shl 21) or
                                          ((data[7].toInt() and 0x7F) shl 14) or
                                          ((data[8].toInt() and 0x7F) shl 7) or
                                          (data[9].toInt() and 0x7F)
                            val totalTagSize = 10 + id3Size
                            if (totalTagSize < data.size) {
                                headerOffset = totalTagSize
                                Log.d("SmoothSeek", "LocalAudioProxy: Stripped $totalTagSize bytes of ID3 from segment")
                            }
                        }
                        
                        Log.d("SmoothSeek", "LocalAudioProxy: Appending HLS segment (${data.size - headerOffset} bytes)")
                        appendData(tag, data, data.size - headerOffset, headerOffset)
                        downloadedSegments.add(segmentPath)
                    }
                }
                if (newSegments.isNotEmpty()) delay(HLS_SEGMENT_DOWNLOAD_DELAY_MS.milliseconds) else delay(HLS_EMPTY_PLAYLIST_DELAY_MS.milliseconds)
            } catch (e: Exception) {
                if (!isRunning.get() || sessionTag != tag) break
                Log.e("SmoothSeek", "LocalAudioProxy: HLS download error: ${e.message}")
                retryCount++
                delay(currentDelay.milliseconds)
                currentDelay = (currentDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
            }
        }
        if (retryCount >= maxRetries) {
            Log.e("SmoothSeek", "LocalAudioProxy: max HLS retries reached")
            terminalError = PlaybackConstants.ERROR_UNREACHABLE
            stop()
        }
    }

    private fun appendData(tag: String, data: ByteArray, length: Int, offset: Int = 0) {
        if (tag != sessionTag || !isRunning.get()) return
        
        stateLock.withLock {
            runCatching {
                memoryBuffer.write(data, offset, length)
                totalBytesWritten += length
                if (currentUrl?.let { !it.contains(".m3u8") && !it.contains("playlist") } == true) {
                    totalBytesReceived = totalBytesWritten
                }
                
                val threshold = if (totalBytesWritten < 128 * 1024) INITIAL_BURST_SIZE else MEMORY_FLUSH_THRESHOLD
                if (memoryBuffer.size() >= threshold) {
                    flushBufferToDiskInternal()
                }
                dataSignal.tryEmit(Unit)
                dataCondition.signalAll()
            }
        }
    }

    private fun flushBufferToDiskInternal() {
        if (memoryBuffer.size() == 0) return
        val p1 = part1File ?: return
        val p2 = part2File ?: return
        
        runCatching {
            if (p1.length() < PART_SIZE) {
                Log.d("SmoothSeek", "LocalAudioProxy: Flushing ${memoryBuffer.size()} bytes to P1")
                FileOutputStream(p1, true).use { memoryBuffer.writeTo(it) }
            } else if (p2.length() < PART_SIZE) {
                Log.d("SmoothSeek", "LocalAudioProxy: Flushing ${memoryBuffer.size()} bytes to P2")
                FileOutputStream(p2, true).use { memoryBuffer.writeTo(it) }
            }
            
            if (p2.length() >= PART_SIZE) {
                val p1Len = p1.length()
                Log.d("SmoothSeek", "LocalAudioProxy: P2 full. Rotating. P1 was $p1Len bytes. totalBytesDropped before: $totalBytesDropped")
                if (p1.delete()) {
                    if (p2.renameTo(p1)) {
                        totalBytesDropped += p1Len
                        metadataMap.headMap(totalBytesDropped).clear()
                        p2.createNewFile()
                        Log.d("SmoothSeek", "LocalAudioProxy: Rotation complete. totalBytesDropped now: $totalBytesDropped")
                    } else {
                        Log.e("SmoothSeek", "LocalAudioProxy: Failed to rename P2 to P1 during rotation! Attempting manual copy.")
                        try {
                            p2.inputStream().use { input ->
                                p1.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            totalBytesDropped += p1Len
                            metadataMap.headMap(totalBytesDropped).clear()
                            p2.delete()
                            p2.createNewFile()
                        } catch (e: Exception) {
                            Log.e("SmoothSeek", "LocalAudioProxy: Manual copy failed too!", e)
                        }
                    }
                } else {
                    Log.e("SmoothSeek", "LocalAudioProxy: Failed to delete P1 during rotation!")
                }
            }
            memoryBuffer.reset()
        }
    }

    private fun handleClient(socket: Socket, tag: String) {
        scope.launch {
            try {
                val input = socket.getInputStream().bufferedReader()
                var rangeStart = 0L
                var line = input.readLine()
                while (!line.isNullOrEmpty()) {
                    if (line.lowercase().startsWith("range: bytes=")) {
                        rangeStart = line.substring("range: bytes=".length).split("-")[0].trim().toLongOrNull() ?: 0L
                    }
                    line = input.readLine()
                }
                val out = socket.getOutputStream()
                val isHls = currentUrl?.contains(".m3u8") == true || currentUrl?.contains("playlist") == true
                val contentType = remoteMimeType ?: if (isHls) "audio/aac" else "audio/mpeg"
                val bitrateHeader = if (remoteBitrate != null) "X-Bitrate: $remoteBitrate\r\n" else ""
                val statusLine = if (rangeStart > 0) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n"
                val rangeHeader = if (rangeStart > 0) "Content-Range: bytes $rangeStart-/*\r\n" else ""
                val header = statusLine + "Content-Type: $contentType\r\n" + "Accept-Ranges: bytes\r\n" + rangeHeader + bitrateHeader + "Connection: close\r\n\r\n"
                out.write(header.toByteArray())

                var lastReadPos = rangeStart
                val buffer = ByteArray(65536)
                while (isRunning.get() && sessionTag == tag && !socket.isClosed) {
                    var physicalFile: File? = null
                    var physicalOffset = 0L
                    var bytesFromMemory = 0
                    stateLock.withLock {
                        val p1Size = part1File?.length() ?: 0L
                        val p2Size = part2File?.length() ?: 0L
                        val totalPhysicalSize = p1Size + p2Size
                        val relativePos = lastReadPos - totalBytesDropped
                        if (relativePos >= 0) {
                            if (relativePos < p1Size) {
                                physicalFile = part1File; physicalOffset = relativePos
                            } else if (relativePos < totalPhysicalSize) {
                                physicalFile = part2File; physicalOffset = relativePos - p1Size
                            } else {
                                val memoryPos = (relativePos - totalPhysicalSize).toInt()
                                val memSize = memoryBuffer.size()
                                if (memoryPos < memSize) {
                                    val toRead = minOf(buffer.size, memSize - memoryPos)
                                    System.arraycopy(memoryBuffer.getInternalBuffer(), memoryPos, buffer, 0, toRead)
                                    bytesFromMemory = toRead
                                }
                            }
                        } else { lastReadPos = totalBytesDropped; physicalFile = part1File; physicalOffset = 0 }
                    }
                    if (physicalFile != null && physicalFile.exists() && physicalFile.length() > physicalOffset) {
                        RandomAccessFile(physicalFile, "r").use { raf ->
                            raf.seek(physicalOffset)
                            val read = raf.read(buffer)
                            if (read > 0) { out.write(buffer, 0, read); lastReadPos += read }
                        }
                    } else if (bytesFromMemory > 0) {
                        out.write(buffer, 0, bytesFromMemory)
                        lastReadPos += bytesFromMemory
                    } else {
                        withContext(ioDispatcher) { withTimeoutOrNull(DATA_WAIT_TIMEOUT_MS.milliseconds) { dataSignal.first() } }
                    }
                }
            } catch (e: Exception) {} finally { runCatching { socket.close() } }
        }
    }

    private fun cleanupLegacyFiles() {
        try { cacheDir.listFiles { f -> f.name.startsWith("proxy_") }?.forEach { it.delete() } } catch (e: Exception) {}
    }

    fun getMetadataForOffset(offset: Long): String? {
        return stateLock.withLock {
            val entry = metadataMap.floorEntry(offset)
            // Log.v("SmoothMetadata", "getMetadataForOffset: offset=$offset -> found=${entry?.value}")
            entry?.value
        }
    }

    /**
     * Reads data from the rolling buffer synchronously.
     * Blocks until data is available or session stops.
     */
    fun readData(tag: String, position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        var waitCount = 0
        try {
            while (isRunning.get() && sessionTag == tag) {
                if (terminalError != 0) {
                    Log.e("SmoothSeek", "LocalAudioProxy.readData: terminal error $terminalError for tag $tag")
                    return -3
                }

                val result = stateLock.withLock {
                    val p1Size = part1File?.length() ?: 0L
                    val p2Size = part2File?.length() ?: 0L
                    val totalPhysicalSize = p1Size + p2Size

                    var currentRelPos = position - totalBytesDropped

                    if (currentRelPos < 0) {
                        // Position is evicted from the buffer
                        Log.w("SmoothSeek", "LocalAudioProxy.readData: EVICTED! ReqPos=$position, totalBytesDropped=$totalBytesDropped, diff=${position - totalBytesDropped}")
                        return@withLock -2 // Signal eviction
                    }

                    val minDataRequired = if (position == 0L) MIN_SNIFF_SIZE.toLong() else 8192L
                    val memoryPos = (currentRelPos - totalPhysicalSize).toInt()
                    val memSize = memoryBuffer.size().toLong()

                    val availableTotal = if (memoryPos < 0) {
                        (totalPhysicalSize + memSize) - currentRelPos
                    } else {
                        memSize - memoryPos.toLong()
                    }

                    if (availableTotal < minDataRequired && isRunning.get() && sessionTag == tag) {
                        // Not enough data yet, wait
                        return@withLock null
                    }

                    var totalRead = 0

                    while (totalRead < length) {
                        val remaining = length - totalRead
                        val currentMemPos = (currentRelPos - totalPhysicalSize).toInt()

                        if (currentMemPos >= 0) {
                            // Read from memory buffer
                            if (currentMemPos < memSize) {
                                val chunk = minOf(remaining.toLong(), memSize - currentMemPos).toInt()
                                System.arraycopy(
                                    memoryBuffer.getInternalBuffer(),
                                    currentMemPos,
                                    buffer,
                                    offset + totalRead,
                                    chunk
                                )
                                totalRead += chunk
                                currentRelPos += chunk
                            } else break
                        } else {
                            // Read from physical files
                            val file: File?
                            val fileOffset: Long
                            val fileSize: Long

                            if (currentRelPos < p1Size) {
                                file = part1File
                                fileOffset = currentRelPos
                                fileSize = p1Size
                            } else if (currentRelPos < totalPhysicalSize) {
                                file = part2File
                                fileOffset = currentRelPos - p1Size
                                fileSize = p2Size
                            } else {
                                file = null
                                fileOffset = 0
                                fileSize = 0
                            }

                            if (file != null && file.exists()) {
                                val chunkLimit = (fileSize - fileOffset).toInt()
                                val chunk = minOf(remaining, chunkLimit)
                                if (chunk > 0) {
                                    var read = 0
                                    try {
                                        RandomAccessFile(file, "r").use { raf ->
                                            raf.seek(fileOffset)
                                            read = raf.read(buffer, offset + totalRead, chunk)
                                        }
                                    } catch (e: Exception) {
                                        Log.e("SmoothSeek", "LocalAudioProxy: Physical read error: ${e.message}")
                                        return@withLock -3
                                    }

                                    if (read > 0) {
                                        totalRead += read
                                        currentRelPos += read
                                    } else break
                                } else break
                            } else break
                        }
                    }

                    if (totalRead > 0) {
                        lastReadPosition = currentRelPos + totalBytesDropped
                        return@withLock totalRead
                    }
                    null
                }

                when (result) {
                    -2 -> return -2 // Evicted
                    -3 -> return -3 // Terminal error
                    is Int -> return result // Bytes read
                    null -> {
                        // Wait for more data
                        stateLock.withLock {
                            if (isRunning.get() && sessionTag == tag) {
                                dataCondition.await(READ_DATA_AWAIT_MS, TimeUnit.MILLISECONDS)
                                waitCount++
                            }
                        }
                    }
                }
            }
            if (terminalError != 0) return -3
        } catch (e: Exception) {
            Log.e("SmoothSeek", "LocalAudioProxy.readData error for tag $tag at position $position: ${e.javaClass.simpleName}: ${e.message}", e)
            return -1
        }
        return -1
    }

    fun stop() {
        Log.d("SmoothSeek", "LocalAudioProxy.stop (wasRunning=${isRunning.get()}, tag=$sessionTag)")
        isRunning.set(false)
        
        // Cancel all coroutines under sessionJob (download, proxy, and client handlers)
        sessionJob.cancelChildren()
        
        stateLock.withLock {
            flushBufferToDiskInternal()
            metadataMap.clear()
            part1File = null
            part2File = null
            dataCondition.signalAll()
            sessionTag = "" // Clear tag while holding lock
        }
        
        totalBytesWritten = 0L
        totalBytesReceived = 0L
        totalBytesDropped = 0L
        sessionStartTime = 0L
        dataSignal.tryEmit(Unit)
        proxyState = ProxyState.Idle
        try { serverSocket?.close() } catch (e: Exception) {}
        cleanupLegacyFiles()
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

    private class FastMemoryBuffer(initialCapacity: Int) {
        private var buffer = ByteArray(initialCapacity)
        private var size = 0
        fun write(data: ByteArray, offset: Int, length: Int) {
            ensureCapacity(size + length)
            System.arraycopy(data, offset, buffer, size, length)
            size += length
        }
        private fun ensureCapacity(minCapacity: Int) {
            if (minCapacity > buffer.size) buffer = buffer.copyOf((buffer.size * 2).coerceAtLeast(minCapacity))
        }
        fun size() = size
        fun reset() { size = 0 }
        fun getInternalBuffer() = buffer
        fun writeTo(out: OutputStream) { out.write(buffer, 0, size) }
    }

    private sealed class ProxyState {
        object Idle : ProxyState()
        object Connecting : ProxyState()
        data class Streaming(val mimeType: String?, val bitrate: String?) : ProxyState()
    }

    companion object {
        const val PART_SIZE = 256 * 1024L
        const val TOTAL_CAPACITY_BYTES = PART_SIZE * 2
        const val MAX_PARALLEL_DOWNLOADS = 6
        const val MEMORY_FLUSH_THRESHOLD = 32 * 1024
        const val INITIAL_BURST_SIZE = 256 * 1024
        const val MIN_SNIFF_SIZE = 32 * 1024

        private const val DEFAULT_READ_TIMEOUT_SEC = 6
        private const val RETRY_READ_TIMEOUT_SEC = 8
        private const val INITIAL_RETRY_DELAY_MS = 700L
        private const val SUCCESS_RETRY_DELAY_MS = 1000L
        private const val MAX_RETRY_DELAY_MS = 30000L
        private const val HLS_PLAYLIST_RETRY_DELAY_MS = 2000L
        private const val HLS_SEGMENT_DOWNLOAD_DELAY_MS = 500L
        private const val HLS_EMPTY_PLAYLIST_DELAY_MS = 4000L
        private const val DATA_WAIT_TIMEOUT_MS = 500L
        private const val READ_DATA_AWAIT_MS = 200L
    }
}
