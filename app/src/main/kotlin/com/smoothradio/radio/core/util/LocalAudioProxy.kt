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
 * A tiny local HTTP proxy that downloads a live stream to a file and serves it to ExoPlayer.
 * This allows "infinite" seeking back on live streams because ExoPlayer thinks it's a static file.
 */
class LocalAudioProxy(private val context: Context) {
    private var serverSocket: ServerSocket? = null
    private var proxyJob: Job? = null
    private var downloadJob: Job? = null
    private val isRunning = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO)
    
    private var bufferFile: File? = null
    private var sessionTag: String = ""
    private var currentUrl: String? = null

    val proxyUrl: String
        get() = "http://127.0.0.1:${serverSocket?.localPort ?: 0}/$sessionTag.mp3"

    fun start(streamUrl: String) {
        // Always stop existing session to ensure we start from the live edge
        stop()

        sessionTag = UUID.randomUUID().toString().take(8)
        
        // Clean up any stray buffer files from previous crashes/sessions
        cleanupLegacyFiles()

        bufferFile = File(context.cacheDir, "proxy_buffer_$sessionTag.mp3").apply {
            if (exists()) delete()
            createNewFile()
        }

        isRunning.set(true)
        serverSocket = ServerSocket(0) 
        
        Log.d("LocalProxy", "Starting New Session [$sessionTag] at $proxyUrl")

        // 1. Background Download
        downloadJob = scope.launch {
            downloadStream(streamUrl)
        }

        // 2. Local Server
        proxyJob = scope.launch {
            while (isRunning.get()) {
                try {
                    val client = serverSocket?.accept() ?: break
                    handleClient(client)
                } catch (e: Exception) {
                    if (isRunning.get()) Log.e("LocalProxy", "Server error", e)
                }
            }
        }
    }

    private suspend fun downloadStream(streamUrl: String) = withContext(Dispatchers.IO) {
        val isHls = streamUrl.contains(".m3u8") || streamUrl.contains("playlist")
        if (isHls) {
            downloadHlsStream(streamUrl)
        } else {
            downloadProgressiveStream(streamUrl)
        }
    }

    private suspend fun downloadProgressiveStream(streamUrl: String) = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null
        try {
            Log.d("LocalProxy", "[$sessionTag] Downloading Progressive: $streamUrl")
            val url = URL(streamUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                Log.e("LocalProxy", "[$sessionTag] Server error code: $responseCode")
                return@withContext
            }

            inputStream = connection.inputStream
            outputStream = FileOutputStream(bufferFile, false)

            val buffer = ByteArray(32768)
            var bytesRead: Int
            while (isRunning.get()) {
                bytesRead = inputStream?.read(buffer) ?: -1
                if (bytesRead == -1) {
                    Log.d("LocalProxy", "[$sessionTag] End of stream")
                    break
                }
                outputStream.write(buffer, 0, bytesRead)
                outputStream.flush()
            }
        } catch (e: Exception) {
            Log.e("LocalProxy", "[$sessionTag] Progressive download failed", e)
        } finally {
            try { inputStream?.close() } catch (e: Exception) {}
            try { outputStream?.close() } catch (e: Exception) {}
        }
    }

    private suspend fun downloadHlsStream(playlistUrl: String): Unit = withContext(Dispatchers.IO) {
        val downloadedSegments = mutableSetOf<String>()
        val baseUrl = playlistUrl.substring(0, playlistUrl.lastIndexOf("/") + 1)
        Log.d("LocalProxy", "[$sessionTag] Downloading HLS: $playlistUrl")
        
        while (isRunning.get()) {
            try {
                val connection = URL(playlistUrl).openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                val playlistText = connection.inputStream.bufferedReader().readText()
                
                val lines = playlistText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                
                // If it's a master playlist, follow the first variant
                if (playlistText.contains("#EXT-X-STREAM-INF")) {
                    val variant = lines.firstOrNull { !it.startsWith("#") }
                    if (variant != null) {
                        val newUrl = if (variant.startsWith("http")) variant else baseUrl + variant
                        Log.d("LocalProxy", "[$sessionTag] Following HLS variant: $newUrl")
                        downloadHlsStream(newUrl)
                        return@withContext
                    }
                }

                val newSegments = lines.filter { !it.startsWith("#") }
                
                for (segmentPath in newSegments) {
                    if (!isRunning.get()) break
                    if (downloadedSegments.contains(segmentPath)) continue
                    
                    val fullSegmentUrl = if (segmentPath.startsWith("http")) segmentPath else baseUrl + segmentPath
                    downloadAndAppendSegment(fullSegmentUrl)
                    downloadedSegments.add(segmentPath)
                }
                
                kotlinx.coroutines.delay(4000)
            } catch (e: Exception) {
                Log.e("LocalProxy", "[$sessionTag] HLS Playlist error", e)
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    private fun downloadAndAppendSegment(segmentUrl: String) {
        try {
            val connection = URL(segmentUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            val inputStream = connection.inputStream
            
            // Read into memory to allow stripping metadata headers (segments are usually small, 200KB-1MB)
            val segmentData = inputStream.readBytes()
            inputStream.close()

            var offset = 0
            // Strip ID3 tags which are often present at the start of HLS segments.
            // When segments are glued, middle ID3 tags break bitrate-based seeking.
            if (segmentData.size > 10 && 
                segmentData[0] == 'I'.code.toByte() && 
                segmentData[1] == 'D'.code.toByte() && 
                segmentData[2] == '3'.code.toByte()) {
                
                val size = ((segmentData[6].toInt() and 0x7F) shl 21) or
                           ((segmentData[7].toInt() and 0x7F) shl 14) or
                           ((segmentData[8].toInt() and 0x7F) shl 7) or
                           (segmentData[9].toInt() and 0x7F)
                offset = 10 + size
                Log.d("LocalProxy", "[$sessionTag] Stripped ID3 metadata ($size bytes) from segment")
            }

            val outputStream = FileOutputStream(bufferFile, true)
            if (offset < segmentData.size) {
                outputStream.write(segmentData, offset, segmentData.size - offset)
            }
            outputStream.flush()
            outputStream.close()
            
            Log.d("LocalProxy", "[$sessionTag] Appended segment. Total buffer: ${bufferFile?.length()} bytes")
        } catch (e: Exception) {
            Log.e("LocalProxy", "[$sessionTag] Segment download failed", e)
        }
    }

    private fun handleClient(socket: Socket) {
        scope.launch {
            try {
                val input = socket.getInputStream().bufferedReader()
                val requestLine = input.readLine() ?: return@launch
                Log.d("LocalProxy", "[$sessionTag] Request: $requestLine")

                var rangeStart = 0L
                var line = input.readLine()
                while (!line.isNullOrEmpty()) {
                    if (line.lowercase().startsWith("range: bytes=")) {
                        val range = line.substring("range: bytes=".length).trim()
                        val dashPos = range.indexOf('-')
                        if (dashPos != -1) {
                            rangeStart = range.substring(0, dashPos).toLongOrNull() ?: 0L
                        }
                    }
                    line = input.readLine()
                }

                val output = socket.getOutputStream()
                val file = bufferFile ?: return@launch

                // Determine MimeType. If segments were .aac or .ts, use appropriate type.
                val isHlsSession = currentUrl?.contains(".m3u8") == true || currentUrl?.contains("playlist") == true
                val contentType = if (isHlsSession) "audio/aac" else "audio/mpeg"

                // A more realistic "Fake Size" (200MB instead of 1GB).
                // This helps ExoPlayer's bitrate estimator converge much faster.
                val totalFakeSize = 200 * 1024 * 1024L

                if (rangeStart > 0) {
                    val header = "HTTP/1.1 206 Partial Content\r\n" +
                            "Content-Type: $contentType\r\n" +
                            "Accept-Ranges: bytes\r\n" +
                            "Content-Range: bytes $rangeStart-${totalFakeSize - 1}/$totalFakeSize\r\n" +
                            "Content-Length: ${totalFakeSize - rangeStart}\r\n" +
                            "Connection: close\r\n\r\n"
                    output.write(header.toByteArray())
                } else {
                    val header = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: $contentType\r\n" +
                            "Accept-Ranges: bytes\r\n" +
                            "Content-Length: $totalFakeSize\r\n" +
                            "Connection: close\r\n\r\n"
                    output.write(header.toByteArray())
                }

                Log.d("LocalProxy", "[$sessionTag] Serving $contentType from Byte: $rangeStart")

                var lastReadPos = rangeStart
                val buffer = ByteArray(16384)

                while (isRunning.get() && !socket.isClosed) {
                    val availableSize = file.length()
                    if (availableSize > lastReadPos) {
                        java.io.RandomAccessFile(file, "r").use { raf ->
                            raf.seek(lastReadPos)
                            var bytesRead = raf.read(buffer)
                            while (bytesRead != -1) {
                                try {
                                    output.write(buffer, 0, bytesRead)
                                    lastReadPos += bytesRead
                                    bytesRead = raf.read(buffer)
                                } catch (e: Exception) {
                                    return@launch
                                }
                            }
                        }
                        output.flush()
                    }
                    kotlinx.coroutines.delay(200)
                }
            } catch (e: Exception) {
                Log.e("LocalProxy", "[$sessionTag] Error in handler", e)
            } finally {
                try { socket.close() } catch (e: Exception) {}
            }
        }
    }

    private fun cleanupLegacyFiles() {
        try {
            context.cacheDir.listFiles { file -> 
                file.name.startsWith("proxy_buffer_") && file.name.endsWith(".mp3") 
            }?.forEach { it.delete() }
        } catch (e: Exception) {}
    }

    fun stop() {
        isRunning.set(false)
        downloadJob?.cancel()
        proxyJob?.cancel()
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        serverSocket = null
        
        // Final attempt to delete current file
        try {
            bufferFile?.delete()
        } catch (e: Exception) {}
        bufferFile = null
    }
}
