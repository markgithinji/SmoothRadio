package com.smoothradio.radio.service.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
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
import java.io.RandomAccessFile
import java.net.ServerSocket
import java.net.Socket
import java.util.TreeMap
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A local HTTP proxy that downloads a live stream to a rolling three-part buffer.
 * Provides a "Time Machine" seeking experience while strictly limiting disk usage.
 */
class LocalAudioProxy(private val context: Context) {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private var serverSocket: ServerSocket? = null
    private var proxyJob: Job? = null
    private var downloadJob: Job? = null
    private val isRunning = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO)
    
    // Signal for handleClient to wake up when new data is available
    private val dataSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    
    // Manual sync object for synchronous ProxyDataSource
    private val lock = Any()

    private var currentUrl: String? = null
    private var sessionTag: String = ""
    private var remoteMimeType: String? = null
    private var remoteBitrate: String? = null
    var detectedBitrateKbps: Double? = null
        private set

    private val metadataMap = TreeMap<Long, String>()
    
    // Memory bursting buffer
    private val memoryBuffer = FastMemoryBuffer(INITIAL_BURST_SIZE)

    // Rolling Buffer State
    var part1File: File? = null
        private set
    var part2File: File? = null
        private set
    var part3File: File? = null
        private set
    var totalBytesDropped = 0L // Total bytes ever purged from history
        private set
    var totalBytesWritten = 0L // Total bytes ever written in this session
        private set
    var totalBytesReceived = 0L // Total bytes ever fetched from network in this session
        private set

    private var sessionStartTime = 0L
    private var firstByteReceivedTime = 0L
    private var firstByteServedTime = 0L

    fun isStartedFor(url: String): Boolean {
        return isRunning.get() && currentUrl == url
    }

    fun start(streamUrl: String) {
        stop() // Decisively stop previous session
        
        sessionStartTime = System.currentTimeMillis()
        firstByteReceivedTime = 0L
        firstByteServedTime = 0L
        currentUrl = streamUrl
        sessionTag = UUID.randomUUID().toString().take(8)
        remoteMimeType = null
        remoteBitrate = null
        cleanupLegacyFiles()

        part1File = File(context.cacheDir, "proxy_${sessionTag}_p1.mp3").apply { createNewFile() }
        part2File = File(context.cacheDir, "proxy_${sessionTag}_p2.mp3").apply { createNewFile() }
        part3File = File(context.cacheDir, "proxy_${sessionTag}_p3.mp3").apply { createNewFile() }
        totalBytesDropped = 0L
        totalBytesWritten = 0L
        totalBytesReceived = 0L

        synchronized(this) {
            metadataMap.clear()
        }

        isRunning.set(true)
        serverSocket = ServerSocket(0)
        
        val tagAtStart = sessionTag
        Log.d("LocalProxy", "Starting Session [$tagAtStart]")

        downloadJob = scope.launch {
            val isHls = streamUrl.contains(".m3u8") || streamUrl.contains("playlist")
            if (isHls) downloadHlsStream(streamUrl, tagAtStart) else downloadProgressiveStream(streamUrl, tagAtStart)
        }

        proxyJob = scope.launch {
            while (isRunning.get() && sessionTag == tagAtStart) {
                try {
                    val client = serverSocket?.accept() ?: break
                    handleClient(client, tagAtStart)
                } catch (e: Exception) {
                    if (isRunning.get()) Log.e("LocalProxy", "Socket error", e)
                }
            }
        }
    }

    private suspend fun downloadProgressiveStream(streamUrl: String, tag: String) = withContext(Dispatchers.IO) {
        var retryCount = 0
        var currentDelay = 1000L
        val maxRetries = 15

        while (isRunning.get() && sessionTag == tag && retryCount < maxRetries) {
            try {
                // Increase timeout as we retry
                val timeout = if (retryCount > 0) 30 else 15
                
                var useMetadata = true
                var response = executeStreamRequest(streamUrl, tag, requestMetadata = true, timeoutSeconds = timeout)

                if (response != null && (response.code == 401 || response.code == 403)) {
                    Log.w("LocalProxy", "[$tag] Server rejected metadata request. Retrying clean...")
                    response.close()
                    useMetadata = false
                    response = executeStreamRequest(streamUrl, tag, requestMetadata = false, timeoutSeconds = timeout)
                }

                response?.use { res ->
                    if (!res.isSuccessful) {
                        Log.e("LocalProxy", "[$tag] Stream failed: ${res.code}")
                        throw Exception("HTTP ${res.code}")
                    }

                    // Success! Reset backoff
                    retryCount = 0
                    currentDelay = 1000L

                    val metaint = if (useMetadata) res.header("icy-metaint")?.toIntOrNull() ?: -1 else -1
                    remoteBitrate = res.header("icy-br")
                    remoteMimeType = res.header("Content-Type")
                    
                    Log.d("LocalProxy", "[$tag] Connected. MIME: $remoteMimeType, BR: $remoteBitrate")
                    
                    val inputStream = res.body?.byteStream() ?: return@use

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
                                    val title = parseIcyMetadata(metadata)
                                    if (title != null) {
                                        synchronized(this@LocalAudioProxy) {
                                            if (metadataMap.lastEntry()?.value != title) {
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
                
                // If we reach here, the stream ended normally or closed. 
                // We retry unless it was a deliberate stop.
                if (isRunning.get() && sessionTag == tag) {
                    Log.w("LocalProxy", "[$tag] Stream connection lost. Reconnecting...")
                    throw Exception("Connection lost")
                }

            } catch (e: Exception) {
                if (!isRunning.get() || sessionTag != tag) break
                retryCount++
                Log.e("LocalProxy", "[$tag] Retry $retryCount/$maxRetries after ${currentDelay}ms: ${e.message}")
                delay(currentDelay)
                currentDelay = (currentDelay * 2).coerceAtMost(30000L)
            }
        }
        
        if (retryCount >= maxRetries) {
            Log.e("LocalProxy", "[$tag] Max retries reached. Stopping.")
            stop()
        }
    }

    private fun executeStreamRequest(url: String, tag: String, requestMetadata: Boolean, timeoutSeconds: Int = 15): Response? {
        val client = if (timeoutSeconds == 10) okHttpClient else okHttpClient.newBuilder()
            .readTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .build()

        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "ExoPlayer/2.18.5")
            .addHeader("Accept", "*/*")
            .addHeader("Connection", "keep-alive")
        
        if (requestMetadata) {
            requestBuilder.addHeader("Icy-MetaData", "1")
        }

        return try {
            client.newCall(requestBuilder.build()).execute()
        } catch (e: Exception) {
            null
        }
    }

    private fun parseIcyMetadata(metadata: String): String? {
        val match = Regex("StreamTitle='(.*?)';", RegexOption.DOT_MATCHES_ALL).find(metadata)
        val rawTitle = match?.groupValues?.get(1) ?: return null
        
        if (rawTitle.startsWith("<?xml") || rawTitle.contains("<LogEvent")) {
            try {
                val titleMatch = Regex("Title=\"(.*?)\"").find(rawTitle)
                val artistMatch = Regex("Artist1=\"(.*?)\"").find(rawTitle)
                val title = titleMatch?.groupValues?.get(1)
                val artist = artistMatch?.groupValues?.get(1)
                if (title != null && artist != null) return "$title - $artist"
                if (title != null) return title
            } catch (e: Exception) {}
        }
        return rawTitle
    }

    private suspend fun downloadHlsStream(playlistUrl: String, tag: String): Unit = withContext(Dispatchers.IO) {
        val downloadedSegments = mutableSetOf<String>()
        val baseUrl = playlistUrl.substring(0, playlistUrl.lastIndexOf("/") + 1)
        var retryCount = 0
        var currentDelay = 1000L

        while (isRunning.get() && sessionTag == tag) {
            try {
                val request = Request.Builder().url(playlistUrl).addHeader("User-Agent", "Mozilla/5.0").build()
                val playlistText = okHttpClient.newCall(request).execute().use { response -> 
                    if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                    response.body?.string() ?: "" 
                }
                
                // Success! Reset backoff
                retryCount = 0
                currentDelay = 1000L

                if (playlistText.isEmpty()) { delay(2000); continue }

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
                        val (bandwidth, info, variantUrl) = bestVariant
                        detectedBitrateKbps = bandwidth.toDouble() / 1000.0
                        
                        Log.d("LocalProxy", "[$tag] HLS Variant: ${detectedBitrateKbps}kbps")

                        val fullUrl = if (variantUrl.startsWith("http")) variantUrl else baseUrl + variantUrl
                        downloadHlsStream(fullUrl, tag)
                        return@withContext
                    }
                }

                val allSegments = lines.filter { !it.startsWith("#") }
                val newSegments = allSegments.filter { !downloadedSegments.contains(it) }

                val tasks = newSegments.take(MAX_PARALLEL_DOWNLOADS).map { segmentPath ->
                    val segmentUrl = if (segmentPath.startsWith("http")) segmentPath else baseUrl + segmentPath
                    scope.async {
                        try {
                            val segRequest = Request.Builder().url(segmentUrl).addHeader("User-Agent", "Mozilla/5.0").build()
                            okHttpClient.newCall(segRequest).execute().use { response ->
                                if (!response.isSuccessful) return@use byteArrayOf()
                                val body = response.body ?: return@use byteArrayOf()
                                val out = ByteArrayOutputStream()
                                val inputStream = body.byteStream()
                                val buffer = ByteArray(8192)
                                var read: Int
                                while (isRunning.get() && sessionTag == tag) {
                                    read = inputStream.read(buffer)
                                    if (read == -1) break
                                    out.write(buffer, 0, read)
                                    synchronized(this@LocalAudioProxy) { totalBytesReceived += read }
                                }
                                out.toByteArray()
                            }
                        } catch (e: Exception) { byteArrayOf() }
                    }
                }

                newSegments.take(MAX_PARALLEL_DOWNLOADS).forEachIndexed { index, segmentPath ->
                    if (!isRunning.get() || sessionTag != tag) return@forEachIndexed
                    val data = tasks[index].await()
                    if (data.isNotEmpty()) {
                        var offset = 0
                        if (data.size > 10 && data[0] == 'I'.code.toByte() && data[1] == 'D'.code.toByte() && data[2] == '3'.code.toByte()) {
                            val size = ((data[6].toInt() and 0x7F) shl 21) or ((data[7].toInt() and 0x7F) shl 14) or ((data[8].toInt() and 0x7F) shl 7) or (data[9].toInt() and 0x7F)
                            if (10 + size < data.size) {
                                val id3Data = data.sliceArray(0 until (10 + size))
                                val title = extractTitleFromId3(id3Data)
                                if (title != null) {
                                    val lastTitle = synchronized(this@LocalAudioProxy) { metadataMap.lastEntry()?.value }
                                    if (title != lastTitle) {
                                        synchronized(this@LocalAudioProxy) { metadataMap[totalBytesWritten] = title }
                                    }
                                }
                                offset = 10 + size
                            }
                        }
                        if (data.size > offset) appendData(tag, data, data.size - offset, offset)
                        downloadedSegments.add(segmentPath)
                    }
                }
                if (newSegments.isNotEmpty()) delay(500) else delay(4000)
            } catch (e: Exception) {
                if (!isRunning.get() || sessionTag != tag) break
                retryCount++
                Log.e("LocalProxy", "[$tag] HLS Retry $retryCount: ${e.message}")
                delay(currentDelay)
                currentDelay = (currentDelay * 2).coerceAtMost(30000L)
            }
        }
    }

    @Synchronized
    private fun appendData(tag: String, data: ByteArray, length: Int, offset: Int = 0) {
        if (tag != sessionTag || !isRunning.get()) return
        if (firstByteReceivedTime == 0L && length > 0) {
            firstByteReceivedTime = System.currentTimeMillis()
            Log.d("LocalProxy", "[$tag] First byte received from network after ${firstByteReceivedTime - sessionStartTime}ms")
        }
        try {
            memoryBuffer.write(data, offset, length)
            totalBytesWritten += length
            if (currentUrl?.contains(".m3u8") == false && !currentUrl?.contains("playlist")!!) {
                totalBytesReceived = totalBytesWritten
            }
            val threshold = if (totalBytesWritten < 128 * 1024) INITIAL_BURST_SIZE else MEMORY_FLUSH_THRESHOLD
            if (memoryBuffer.size() >= threshold) flushBufferToDisk()
            dataSignal.tryEmit(Unit)
            synchronized(lock) {
                (lock as java.lang.Object).notifyAll()
            }
        } catch (e: Exception) { Log.e("LocalProxy", "Buffer error", e) }
    }

    @Synchronized
    private fun flushBufferToDisk() {
        if (memoryBuffer.size() == 0) return
        val p1 = part1File ?: return
        val p2 = part2File ?: return
        val p3 = part3File ?: return
        try {
            if (p1.length() < PART_SIZE) {
                FileOutputStream(p1, true).use { memoryBuffer.writeTo(it) }
            } else if (p2.length() < PART_SIZE) {
                FileOutputStream(p2, true).use { memoryBuffer.writeTo(it) }
            } else {
                FileOutputStream(p3, true).use { memoryBuffer.writeTo(it) }
                if (p3.length() >= PART_SIZE) {
                    totalBytesDropped += p1.length()
                    metadataMap.headMap(totalBytesDropped).clear()
                    // Rotate: Delete P1, move P2 to P1, P3 to P2, recreate P3
                    p1.delete()
                    p2.renameTo(p1)
                    p3.renameTo(p2)
                    p3.createNewFile()
                }
            }
            memoryBuffer.reset()
        } catch (e: Exception) { Log.e("LocalProxy", "Storage error", e) }
    }

    private fun handleClient(socket: Socket, tag: String) {
        scope.launch {
            try {
                val input = socket.getInputStream().bufferedReader()
                val requestLine = input.readLine() ?: return@launch
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

                val header = statusLine +
                        "Content-Type: $contentType\r\n" +
                        "Accept-Ranges: bytes\r\n" +
                        rangeHeader +
                        bitrateHeader +
                        "Connection: close\r\n\r\n"
                out.write(header.toByteArray())

                var lastReadPos = rangeStart
                val buffer = ByteArray(65536)
                while (isRunning.get() && sessionTag == tag && !socket.isClosed) {
                    var physicalFile: File? = null
                    var physicalOffset = 0L
                    var bytesFromMemory = 0
                    synchronized(this@LocalAudioProxy) {
                        val p1Size = part1File?.length() ?: 0L
                        val p2Size = part2File?.length() ?: 0L
                        val p3Size = part3File?.length() ?: 0L
                        val totalPhysicalSize = p1Size + p2Size + p3Size
                        val relativePos = lastReadPos - totalBytesDropped
                        if (relativePos >= 0) {
                            if (relativePos < p1Size) { physicalFile = part1File; physicalOffset = relativePos }
                            else if (relativePos < p1Size + p2Size) { physicalFile = part2File; physicalOffset = relativePos - p1Size }
                            else if (relativePos < totalPhysicalSize) { physicalFile = part3File; physicalOffset = relativePos - p1Size - p2Size }
                            else {
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
                    if (physicalFile != null && physicalFile!!.exists() && physicalFile!!.length() > physicalOffset) {
                        if (firstByteServedTime == 0L) {
                            firstByteServedTime = System.currentTimeMillis()
                            Log.d("LocalProxy", "[$tag] First byte served to client from disk after ${firstByteServedTime - sessionStartTime}ms")
                        }
                        RandomAccessFile(physicalFile, "r").use { raf ->
                            raf.seek(physicalOffset)
                            val read = raf.read(buffer)
                            if (read > 0) { out.write(buffer, 0, read); lastReadPos += read }
                        }
                    } else if (bytesFromMemory > 0) {
                        if (firstByteServedTime == 0L) {
                            firstByteServedTime = System.currentTimeMillis()
                            Log.d("LocalProxy", "[$tag] First byte served to client from memory after ${firstByteServedTime - sessionStartTime}ms")
                        }
                        out.write(buffer, 0, bytesFromMemory)
                        lastReadPos += bytesFromMemory
                    } else { withTimeoutOrNull(500) { dataSignal.first() } }
                }
            } catch (e: Exception) {} finally { try { socket.close() } catch (e: Exception) {} }
        }
    }

    private fun cleanupLegacyFiles() {
        try { context.cacheDir.listFiles { f -> f.name.startsWith("proxy_") }?.forEach { it.delete() } } catch (e: Exception) {}
    }

    private fun extractTitleFromId3(data: ByteArray): String? {
        try {
            var i = 10 
            while (i + 10 < data.size) {
                if (i + 4 > data.size) break
                val frameId = String(data, i, 4)
                if (frameId.all { it == '\u0000' }) break
                val frameSize = ((data[i + 4].toInt() and 0xFF) shl 24) or ((data[i + 5].toInt() and 0xFF) shl 16) or ((data[i + 6].toInt() and 0xFF) shl 8) or (data[i + 7].toInt() and 0xFF)
                if (frameId == "TIT2" || frameId == "TPE1") {
                    val encoding = if (i + 10 < data.size) data[i + 10].toInt() else 0
                    val textOffset = i + 11
                    val textLength = frameSize - 1
                    if (textOffset + textLength <= data.size && textLength > 0) {
                        return String(data, textOffset, textLength, if (encoding == 1) Charsets.UTF_16 else Charsets.UTF_8).trim { it <= ' ' || it == '\u0000' }
                    }
                }
                if (frameSize <= 0) break
                i += 10 + frameSize
            }
        } catch (e: Exception) {}
        return null
    }

    fun getMetadataForOffset(offset: Long): String? { synchronized(this) { return metadataMap.floorEntry(offset)?.value } }

    /**
     * Reads data from the rolling buffer synchronously.
     * Blocks until data is available or session stops.
     */
    fun readData(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        val tag = sessionTag
        try {
            while (isRunning.get() && sessionTag == tag) {
                var physicalFile: File? = null
                var physicalOffset = 0L

                synchronized(this) {
                    val p1Size = part1File?.length() ?: 0L
                    val p2Size = part2File?.length() ?: 0L
                    val p3Size = part3File?.length() ?: 0L
                    val totalPhysicalSize = p1Size + p2Size + p3Size
                    val relativePos = position - totalBytesDropped

                    if (relativePos >= 0) {
                        if (relativePos < p1Size) {
                            physicalFile = part1File
                            physicalOffset = relativePos
                        } else if (relativePos < p1Size + p2Size) {
                            physicalFile = part2File
                            physicalOffset = relativePos - p1Size
                        } else if (relativePos < totalPhysicalSize) {
                            physicalFile = part3File
                            physicalOffset = relativePos - p1Size - p2Size
                        } else {
                            val memoryPos = (relativePos - totalPhysicalSize).toInt()
                            val memSize = memoryBuffer.size()
                            if (memoryPos < memSize) {
                                val toRead = minOf(length, memSize - memoryPos)
                                System.arraycopy(memoryBuffer.getInternalBuffer(), memoryPos, buffer, offset, toRead)
                                return toRead
                            }
                        }
                    } else {
                        // Position already dropped
                        return -2 
                    }
                }

                if (physicalFile != null && physicalFile!!.exists() && physicalFile!!.length() > physicalOffset) {
                    RandomAccessFile(physicalFile, "r").use { raf ->
                        raf.seek(physicalOffset)
                        return raf.read(buffer, offset, length)
                    }
                }

                // No data yet, wait
                synchronized(lock) {
                    if (isRunning.get() && sessionTag == tag) {
                        (lock as java.lang.Object).wait(500)
                    }
                }
            }
        } catch (e: InterruptedException) {
            return 0
        }
        return -1 // EOF
    }

    fun stop() {
        isRunning.set(false)
        sessionTag = "" 
        totalBytesWritten = 0L
        totalBytesDropped = 0L
        synchronized(this) { flushBufferToDisk(); metadataMap.clear() }
        downloadJob?.cancel()
        proxyJob?.cancel()
        try { serverSocket?.close() } catch (e: Exception) {}
        cleanupLegacyFiles()
    }

    /**
     * A specialized memory buffer that avoids unnecessary copies when reading or flushing.
     */
    private class FastMemoryBuffer(initialCapacity: Int) {
        private var buffer = ByteArray(initialCapacity)
        private var size = 0

        fun write(data: ByteArray, offset: Int, length: Int) {
            ensureCapacity(size + length)
            System.arraycopy(data, offset, buffer, size, length)
            size += length
        }

        private fun ensureCapacity(minCapacity: Int) {
            if (minCapacity > buffer.size) {
                var newSize = buffer.size * 2
                if (newSize < minCapacity) newSize = minCapacity
                buffer = buffer.copyOf(newSize)
            }
        }

        fun size() = size
        fun reset() { size = 0 }
        fun getInternalBuffer() = buffer

        fun writeTo(out: java.io.OutputStream) {
            out.write(buffer, 0, size)
        }
    }

    companion object {
        const val PART_SIZE = 1 * 1024 * 1024L // 1MB per part
        const val TOTAL_CAPACITY_BYTES = PART_SIZE * 3 // 3 Parts for Triple Buffering
        const val MAX_PARALLEL_DOWNLOADS = 6
        const val MEMORY_FLUSH_THRESHOLD = 32 * 1024 // 32KB Burst size
        const val INITIAL_BURST_SIZE = 256 * 1024    // 256KB initial burst for instant player start
    }
}
