package com.smoothradio.radio.core.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A local HTTP proxy that downloads a live stream to a rolling two-part buffer.
 * Provides a "Time Machine" seeking experience while strictly limiting disk usage to ~200MB.
 */
class LocalAudioProxy(private val context: Context) {
    companion object {
        const val BYTES_PER_MS = 16L // ~128kbps (16 bytes per millisecond)
        const val PART_SIZE = 12 * 1024 * 1024L // 12MB per part (Total 24MB ~25 mins)
        const val TOTAL_CAPACITY_BYTES = PART_SIZE * 2
    }

    private var serverSocket: ServerSocket? = null
    private var proxyJob: Job? = null
    private var downloadJob: Job? = null
    private val isRunning = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO)
    
    private var currentUrl: String? = null
    private var sessionTag: String = ""

    // Rolling Buffer State
    private var part1File: File? = null
    private var part2File: File? = null
    private var totalBytesDropped = 0L // Total bytes ever purged from history
    private var totalBytesWritten = 0L // Total bytes ever written in this session

    val proxyUrl: String
        get() = "http://127.0.0.1:${serverSocket?.localPort ?: 0}/$sessionTag.mp3"

    fun getDroppedDurationMs(): Long {
        return totalBytesDropped / BYTES_PER_MS
    }

    fun start(streamUrl: String) {
        stop() // Decisively stop previous session
        
        currentUrl = streamUrl
        sessionTag = UUID.randomUUID().toString().take(8)
        cleanupLegacyFiles()

        part1File = File(context.cacheDir, "proxy_${sessionTag}_p1.mp3").apply { createNewFile() }
        part2File = File(context.cacheDir, "proxy_${sessionTag}_p2.mp3").apply { createNewFile() }
        totalBytesDropped = 0L
        totalBytesWritten = 0L

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
        var inputStream: InputStream? = null
        try {
            val connection = URL(streamUrl).openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.connectTimeout = 15000
            inputStream = connection.inputStream

            val buffer = ByteArray(65536)
            var bytesRead: Int
            while (isRunning.get() && sessionTag == tag) {
                bytesRead = inputStream?.read(buffer) ?: -1
                if (bytesRead == -1) break
                appendData(tag, buffer, bytesRead)
            }
        } catch (e: Exception) {
            Log.e("LocalProxy", "[$tag] Download failed", e)
        } finally {
            try { inputStream?.close() } catch (e: Exception) {}
        }
    }

    private suspend fun downloadHlsStream(playlistUrl: String, tag: String): Unit = withContext(Dispatchers.IO) {
        val downloadedSegments = mutableSetOf<String>()
        val baseUrl = playlistUrl.substring(0, playlistUrl.lastIndexOf("/") + 1)
        
        while (isRunning.get() && sessionTag == tag) {
            try {
                val connection = URL(playlistUrl).openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                val playlistText = connection.inputStream.bufferedReader().readText()
                val lines = playlistText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                
                if (playlistText.contains("#EXT-X-STREAM-INF")) {
                    val variant = lines.firstOrNull { !it.startsWith("#") }
                    if (variant != null) {
                        downloadHlsStream(if (variant.startsWith("http")) variant else baseUrl + variant, tag)
                        return@withContext
                    }
                }

                for (segmentPath in lines.filter { !it.startsWith("#") }) {
                    if (!isRunning.get() || sessionTag != tag) break
                    if (downloadedSegments.contains(segmentPath)) continue
                    
                    try {
                        val segmentData = URL(if (segmentPath.startsWith("http")) segmentPath else baseUrl + segmentPath)
                            .openConnection().apply { connectTimeout = 10000 }.inputStream.readBytes()
                        
                        var offset = 0
                        if (segmentData.size > 10 && segmentData[0] == 'I'.code.toByte() && segmentData[1] == 'D'.code.toByte()) {
                            val size = ((segmentData[6].toInt() and 0x7F) shl 21) or ((segmentData[7].toInt() and 0x7F) shl 14) or
                                       ((segmentData[8].toInt() and 0x7F) shl 7) or (segmentData[9].toInt() and 0x7F)
                            offset = 10 + size
                        }
                        
                        if (offset < segmentData.size) {
                            appendData(tag, segmentData, segmentData.size, offset)
                        }
                        downloadedSegments.add(segmentPath)
                    } catch (e: Exception) {}
                }
                kotlinx.coroutines.delay(4000)
            } catch (e: Exception) {
                kotlinx.coroutines.delay(2000)
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
                    totalBytesDropped += p1.length()
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

                if (rangeStart > 0) {
                    val header = "HTTP/1.1 206 Partial Content\r\nContent-Type: $contentType\r\nAccept-Ranges: bytes\r\n" +
                            "Content-Range: bytes $rangeStart-${TOTAL_CAPACITY_BYTES - 1}/$TOTAL_CAPACITY_BYTES\r\n" +
                            "Content-Length: ${TOTAL_CAPACITY_BYTES - rangeStart}\r\nConnection: close\r\n\r\n"
                    out.write(header.toByteArray())
                } else {
                    val header = "HTTP/1.1 200 OK\r\nContent-Type: $contentType\r\nAccept-Ranges: bytes\r\n" +
                            "Content-Length: $TOTAL_CAPACITY_BYTES\r\nConnection: close\r\n\r\n"
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

    fun stop() {
        isRunning.set(false)
        sessionTag = "" // Invalidate current session immediately
        downloadJob?.cancel()
        proxyJob?.cancel()
        try { serverSocket?.close() } catch (e: Exception) {}
        cleanupLegacyFiles()
    }
}
