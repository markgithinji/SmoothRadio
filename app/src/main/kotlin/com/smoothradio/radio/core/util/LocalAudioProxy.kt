package com.smoothradio.radio.core.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.TreeMap
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A local HTTP proxy that downloads a live stream to a rolling two-part buffer.
 * Provides a "Time Machine" seeking experience while strictly limiting disk usage to ~200MB.
 */
class LocalAudioProxy(private val context: Context) {
    companion object {
        const val BYTES_PER_MS = 16L // ~128kbps (16 bytes per millisecond)
        const val PART_SIZE = 1 * 1024 * 1024L // 1MB per part (Total 2MB ~2 mins)
        const val TOTAL_CAPACITY_BYTES = PART_SIZE * 2
        const val MAX_PARALLEL_DOWNLOADS = 3
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private var serverSocket: ServerSocket? = null
    private var proxyJob: Job? = null
    private var downloadJob: Job? = null
    private val isRunning = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO)
    
    private var currentUrl: String? = null
    private var sessionTag: String = ""
    private val metadataMap = TreeMap<Long, String>()

    // Rolling Buffer State
    var part1File: File? = null
        private set
    var part2File: File? = null
        private set
    var totalBytesDropped = 0L // Total bytes ever purged from history
        private set
    var totalBytesWritten = 0L // Total bytes ever written in this session
        private set

    val proxyUrl: String
        get() = "http://127.0.0.1:${serverSocket?.localPort ?: 0}/$sessionTag.mp3"

    fun start(streamUrl: String) {
        stop() // Decisively stop previous session
        
        currentUrl = streamUrl
        sessionTag = UUID.randomUUID().toString().take(8)
        cleanupLegacyFiles()

        part1File = File(context.cacheDir, "proxy_${sessionTag}_p1.mp3").apply { createNewFile() }
        part2File = File(context.cacheDir, "proxy_${sessionTag}_p2.mp3").apply { createNewFile() }
        totalBytesDropped = 0L
        totalBytesWritten = 0L

        synchronized(this) {
            metadataMap.clear()
        }

        isRunning.set(true)
        serverSocket = ServerSocket(0)
        
        val tagAtStart = sessionTag // Capture the tag for this specific job
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
        try {
            val request = Request.Builder()
                .url(streamUrl)
                .addHeader("User-Agent", "Mozilla/5.0")
                .addHeader("Icy-MetaData", "1")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("LocalProxy", "[$tag] Stream request failed: ${response.code}")
                    return@withContext
                }

                val metaint = response.header("icy-metaint")?.toIntOrNull() ?: -1
                val inputStream = response.body?.byteStream() ?: return@withContext

                Log.d("LocalProxy", "[$tag] Connected to progressive stream via OkHttp. metaint: $metaint")

                if (metaint > 0) {
                    var bytesUntilMetadata = metaint
                    while (isRunning.get() && sessionTag == tag) {
                        if (bytesUntilMetadata > 0) {
                            val buf = ByteArray(minOf(bytesUntilMetadata, 16384))
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
                                Log.v("LocalProxy", "[$tag] Raw ICY metadata: $metadata")
                                val title = parseIcyMetadata(metadata)
                                if (title != null) {
                                    Log.d("LocalProxy", "[$tag] Parsed ICY Title: $title at offset $totalBytesWritten")
                                    synchronized(this@LocalAudioProxy) {
                                        metadataMap[totalBytesWritten] = title
                                    }
                                }
                            }
                            bytesUntilMetadata = metaint
                        }
                    }
                } else {
                    val buffer = ByteArray(65536)
                    var bytesRead: Int
                    while (isRunning.get() && sessionTag == tag) {
                        bytesRead = inputStream.read(buffer)
                        if (bytesRead == -1) break
                        appendData(tag, buffer, bytesRead)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("LocalProxy", "[$tag] Download failed", e)
        }
    }

    private fun parseIcyMetadata(metadata: String): String? {
        // Use DOT_MATCHES_ALL because XML metadata often contains newlines
        val match = Regex("StreamTitle='(.*?)';", RegexOption.DOT_MATCHES_ALL).find(metadata)
        return match?.groupValues?.get(1)
    }

    private suspend fun downloadHlsStream(playlistUrl: String, tag: String): Unit = withContext(Dispatchers.IO) {
        val downloadedSegments = mutableSetOf<String>()
        val baseUrl = playlistUrl.substring(0, playlistUrl.lastIndexOf("/") + 1)
        
        while (isRunning.get() && sessionTag == tag) {
            try {
                val request = Request.Builder()
                    .url(playlistUrl)
                    .addHeader("User-Agent", "Mozilla/5.0")
                    .build()

                val playlistText = okHttpClient.newCall(request).execute().use { response ->
                    response.body?.string() ?: ""
                }
                
                if (playlistText.isEmpty()) {
                    delay(2000)
                    continue
                }

                val lines = playlistText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                
                if (playlistText.contains("#EXT-X-STREAM-INF")) {
                    val variant = lines.firstOrNull { !it.startsWith("#") }
                    if (variant != null) {
                        val variantUrl = if (variant.startsWith("http")) variant else baseUrl + variant
                        downloadHlsStream(variantUrl, tag)
                        return@withContext
                    }
                }

                val allSegments = lines.filter { !it.startsWith("#") }
                val newSegments = allSegments.filter { !downloadedSegments.contains(it) }

                // Download multiple segments in parallel to fill the buffer faster
                val tasks = newSegments.take(MAX_PARALLEL_DOWNLOADS).map { segmentPath ->
                    val segmentUrl = if (segmentPath.startsWith("http")) segmentPath else baseUrl + segmentPath
                    scope.async {
                        try {
                            val segRequest = Request.Builder()
                                .url(segmentUrl)
                                .addHeader("User-Agent", "Mozilla/5.0")
                                .build()
                            
                            okHttpClient.newCall(segRequest).execute().use { response ->
                                response.body?.bytes() ?: byteArrayOf()
                            }
                        } catch (e: Exception) {
                            byteArrayOf()
                        }
                    }
                }

                // Process them in order to maintain stream continuity
                newSegments.take(MAX_PARALLEL_DOWNLOADS).forEachIndexed { index, segmentPath ->
                    if (!isRunning.get() || sessionTag != tag) return@forEachIndexed
                    
                    val data = tasks[index].await()
                    if (data.isNotEmpty()) {
                        var offset = 0
                        // Check for ID3 metadata at the start of the segment
                        if (data.size > 10 && data[0] == 'I'.code.toByte() && data[1] == 'D'.code.toByte() && data[2] == '3'.code.toByte()) {
                            val size = ((data[6].toInt() and 0x7F) shl 21) or 
                                       ((data[7].toInt() and 0x7F) shl 14) or
                                       ((data[8].toInt() and 0x7F) shl 7) or 
                                       (data[9].toInt() and 0x7F)
                            
                            if (10 + size < data.size) {
                                val id3Data = data.sliceArray(0 until (10 + size))
                                val title = extractTitleFromId3(id3Data)
                                if (title != null) {
                                    Log.d("LocalProxy", "[$tag] Extracted ID3: $title")
                                    synchronized(this@LocalAudioProxy) {
                                        metadataMap[totalBytesWritten] = title
                                    }
                                }
                                offset = 10 + size
                            }
                        }
                        
                        if (data.size > offset) {
                            appendData(tag, data, data.size - offset, offset)
                        }
                        downloadedSegments.add(segmentPath)
                    }
                }

                // Prune history to keep memory usage low
                if (downloadedSegments.size > 100) {
                    val list = downloadedSegments.toList()
                    downloadedSegments.clear()
                    downloadedSegments.addAll(list.takeLast(50))
                }

                delay(4000)
            } catch (e: Exception) {
                delay(2000)
            }
        }
    }

    @Synchronized
    private fun appendData(tag: String, data: ByteArray, length: Int, offset: Int = 0) {
        // REJECT DATA FROM STALE SESSIONS
        if (tag != sessionTag || !isRunning.get()) return
        
        val p1 = part1File ?: return
        val p2 = part2File ?: return
        val bytesToWrite = length - offset

        try {
            if (p1.length() < PART_SIZE) {
                FileOutputStream(p1, true).use { it.write(data, offset, bytesToWrite) }
            } else {
                FileOutputStream(p2, true).use { it.write(data, offset, bytesToWrite) }
                
                if (p2.length() >= PART_SIZE) {
                    Log.d("LocalProxy", "[$sessionTag] Buffer Rollover: Purging Part 1, rotating Part 2. Total Dropped: ${totalBytesDropped + p1.length()} bytes")
                    totalBytesDropped += p1.length()
                    metadataMap.headMap(totalBytesDropped).clear()
                    p1.delete()
                    p2.renameTo(p1)
                    p2.createNewFile()
                }
            }
            totalBytesWritten += bytesToWrite
        } catch (e: Exception) {
            Log.e("LocalProxy", "Storage error", e)
        }
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
                val contentType = if (isHls) "audio/aac" else "audio/mpeg"
                
                // Tell ExoPlayer the file is very large (e.g., 1GB ~18 hours) 
                // so it doesn't stop playing when it reaches the physical buffer limit.
                val virtualCapacity = 1024 * 1024 * 1024L 

                if (rangeStart > 0) {
                    val header = "HTTP/1.1 206 Partial Content\r\nContent-Type: $contentType\r\nAccept-Ranges: bytes\r\n" +
                            "Content-Range: bytes $rangeStart-${virtualCapacity - 1}/$virtualCapacity\r\n" +
                            "Content-Length: ${virtualCapacity - rangeStart}\r\nConnection: close\r\n\r\n"
                    out.write(header.toByteArray())
                } else {
                    val header = "HTTP/1.1 200 OK\r\nContent-Type: $contentType\r\nAccept-Ranges: bytes\r\n" +
                            "Content-Length: $virtualCapacity\r\nConnection: close\r\n\r\n"
                    out.write(header.toByteArray())
                }

                var lastReadPos = rangeStart
                val buffer = ByteArray(65536)
                
                while (isRunning.get() && sessionTag == tag && !socket.isClosed) {
                    var physicalFile: File? = null
                    var physicalOffset = 0L

                    synchronized(this@LocalAudioProxy) {
                        val p1Size = part1File?.length() ?: 0L
                        val relativePos = lastReadPos - totalBytesDropped
                        
                        if (relativePos >= 0) {
                            if (relativePos < p1Size) {
                                physicalFile = part1File
                                physicalOffset = relativePos
                            } else {
                                physicalFile = part2File
                                physicalOffset = relativePos - p1Size
                            }
                        } else {
                            // DATA PURGED! Jump forward to the start of available data
                            lastReadPos = totalBytesDropped
                            physicalFile = part1File
                            physicalOffset = 0
                        }
                    }

                    if (physicalFile != null && physicalFile!!.exists() && physicalFile!!.length() > physicalOffset) {
                        java.io.RandomAccessFile(physicalFile, "r").use { raf ->
                            raf.seek(physicalOffset)
                            val read = raf.read(buffer)
                            if (read > 0) {
                                out.write(buffer, 0, read)
                                lastReadPos += read
                            }
                        }
                    } else {
                        kotlinx.coroutines.delay(200)
                    }
                }
            } catch (e: Exception) {
            } finally {
                try { socket.close() } catch (e: Exception) {}
            }
        }
    }

    private fun cleanupLegacyFiles() {
        try {
            context.cacheDir.listFiles { f -> f.name.startsWith("proxy_") }?.forEach { it.delete() }
        } catch (e: Exception) {}
    }

    private fun extractTitleFromId3(data: ByteArray): String? {
        try {
            var i = 10 // Skip header
            while (i + 10 < data.size) {
                if (i + 4 > data.size) break
                val frameId = String(data, i, 4)
                if (frameId.all { it == '\u0000' }) break
                
                val frameSize = ((data[i + 4].toInt() and 0xFF) shl 24) or
                                ((data[i + 5].toInt() and 0xFF) shl 16) or
                                ((data[i + 6].toInt() and 0xFF) shl 8) or
                                (data[i + 7].toInt() and 0xFF)
                
                if (frameId == "TIT2" || frameId == "TPE1") {
                    val encoding = if (i + 10 < data.size) data[i + 10].toInt() else 0
                    val textOffset = i + 11
                    val textLength = frameSize - 1
                    if (textOffset + textLength <= data.size && textLength > 0) {
                        return String(data, textOffset, textLength, if (encoding == 1) Charsets.UTF_16 else Charsets.UTF_8)
                            .trim { it <= ' ' || it == '\u0000' }
                    }
                }
                if (frameSize <= 0) break
                i += 10 + frameSize
            }
        } catch (e: Exception) {}
        return null
    }

    fun getMetadataForOffset(offset: Long): String? {
        synchronized(this) {
            val entry = metadataMap.floorEntry(offset)
            return entry?.value
        }
    }

    fun stop() {
        isRunning.set(false)
        sessionTag = "" // Invalidate current session immediately
        totalBytesWritten = 0L
        totalBytesDropped = 0L
        synchronized(this) {
            metadataMap.clear()
        }
        downloadJob?.cancel()
        proxyJob?.cancel()
        try { serverSocket?.close() } catch (e: Exception) {}
        cleanupLegacyFiles()
    }
}
