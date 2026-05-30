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
    
    private var currentUrl: String? = null
    private var bufferFile: File? = null

    val proxyUrl: String
        get() = "http://127.0.0.1:${serverSocket?.localPort ?: 0}/live.mp3"

    fun start(streamUrl: String) {
        if (isRunning.get() && currentUrl == streamUrl) return
        stop()

        currentUrl = streamUrl
        bufferFile = File(context.cacheDir, "live_buffer.mp3").apply {
            if (exists()) delete()
            createNewFile()
        }

        isRunning.set(true)
        serverSocket = ServerSocket(0) // Bind to any random available port
        
        Log.d("LocalProxy", "Proxy started at $proxyUrl for $streamUrl")

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
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null
        try {
            val url = URL(streamUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            inputStream = connection.inputStream
            outputStream = FileOutputStream(bufferFile, true)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (isRunning.get()) {
                bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break
                outputStream.write(buffer, 0, bytesRead)
                outputStream.flush()
            }
        } catch (e: Exception) {
            Log.e("LocalProxy", "Download interrupted", e)
        } finally {
            inputStream?.close()
            outputStream?.close()
        }
    }

    private fun handleClient(socket: Socket) {
        scope.launch {
            try {
                val input = socket.getInputStream().bufferedReader()
                val requestLine = input.readLine() ?: return@launch
                Log.d("LocalProxy", "Incoming Request: $requestLine")
                
                var rangeStart = 0L
                var line = input.readLine()
                while (!line.isNullOrEmpty()) {
                    Log.d("LocalProxy", "Header: $line")
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
                
                // Wait until we have enough data to satisfy the range request
                var waitCount = 0
                while (file.length() <= rangeStart && waitCount < 50 && isRunning.get()) {
                    kotlinx.coroutines.delay(100)
                    waitCount++
                }

                val currentSize = file.length()
                Log.d("LocalProxy", "Serving Range: $rangeStart, Current File Size: $currentSize")

                if (rangeStart > 0) {
                    // Send 206 Partial Content for seeks
                    // We provide a huge total size (1GB) so ExoPlayer knows it can keep seeking
                    val header = "HTTP/1.1 206 Partial Content\r\n" +
                            "Content-Type: audio/mpeg\r\n" +
                            "Accept-Ranges: bytes\r\n" +
                            "Content-Range: bytes $rangeStart-${currentSize - 1}/1073741824\r\n" +
                            "Content-Length: ${1073741824 - rangeStart}\r\n" +
                            "Connection: close\r\n\r\n"
                    output.write(header.toByteArray())
                } else {
                    // Send 200 OK for initial play
                    val header = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: audio/mpeg\r\n" +
                            "Accept-Ranges: bytes\r\n" +
                            "Content-Length: 1073741824\r\n" +
                            "Connection: close\r\n\r\n"
                    output.write(header.toByteArray())
                }

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
                                    Log.d("LocalProxy", "Socket closed by player")
                                    return@launch
                                }
                            }
                        }
                        output.flush()
                    }
                    kotlinx.coroutines.delay(100)
                }
            } catch (e: Exception) {
                Log.e("LocalProxy", "Error in client handler", e)
            } finally {
                try { socket.close() } catch (e: Exception) {}
            }
        }
    }

    fun stop() {
        isRunning.set(false)
        downloadJob?.cancel()
        proxyJob?.cancel()
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        serverSocket = null
        bufferFile?.delete()
    }
}
