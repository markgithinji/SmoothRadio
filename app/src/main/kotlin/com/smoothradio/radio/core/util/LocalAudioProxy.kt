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
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null
        try {
            val url = URL(streamUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            inputStream = connection.inputStream
            
            // USE APPEND=FALSE TO ENSURE WE START FRESH
            outputStream = FileOutputStream(bufferFile, false)

            val buffer = ByteArray(16384)
            var bytesRead: Int
            while (isRunning.get()) {
                bytesRead = inputStream?.read(buffer) ?: -1
                if (bytesRead == -1) break
                outputStream.write(buffer, 0, bytesRead)
                outputStream.flush()
            }
        } catch (e: Exception) {
            Log.e("LocalProxy", "Download interrupted for $sessionTag", e)
        } finally {
            try { inputStream?.close() } catch (e: Exception) {}
            try { outputStream?.close() } catch (e: Exception) {}
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
                
                // Safety wait for initial data
                var waitCount = 0
                while (file.length() <= rangeStart && waitCount < 50 && isRunning.get()) {
                    kotlinx.coroutines.delay(100)
                    waitCount++
                }

                val currentSize = file.length()
                Log.d("LocalProxy", "[$sessionTag] Serving Range: $rangeStart, Current Buffer: $currentSize")

                if (rangeStart > 0) {
                    val header = "HTTP/1.1 206 Partial Content\r\n" +
                            "Content-Type: audio/mpeg\r\n" +
                            "Accept-Ranges: bytes\r\n" +
                            "Content-Range: bytes $rangeStart-${currentSize - 1}/1073741824\r\n" +
                            "Content-Length: ${1073741824 - rangeStart}\r\n" +
                            "Connection: close\r\n\r\n"
                    output.write(header.toByteArray())
                } else {
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
