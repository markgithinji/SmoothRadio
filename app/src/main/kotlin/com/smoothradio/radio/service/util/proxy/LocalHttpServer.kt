package com.smoothradio.radio.service.util.proxy

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.ServerSocket
import java.net.Socket
import kotlin.time.Duration.Companion.milliseconds

class LocalHttpServer(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val cache: RollingDiskCache,
    private val dataSignal: MutableSharedFlow<Unit>,
    private val isRunning: () -> Boolean,
    private val sessionTag: () -> String,
    private val getMimeType: () -> String?,
    private val getBitrate: () -> String?,
    private val currentUrl: () -> String?
) {
    private var serverSocket: ServerSocket? = null

    fun start(tag: String): Int {
        val socket = ServerSocket(0)
        serverSocket = socket
        val port = socket.localPort

        scope.launch {
            while (isRunning() && sessionTag() == tag) {
                runCatching {
                    val client = serverSocket?.accept() ?: return@launch
                    handleClient(client, tag)
                }
            }
        }
        return port
    }

    private fun handleClient(socket: Socket, tag: String) {
        scope.launch {
            try {
                val input = socket.getInputStream().bufferedReader()
                var rangeStart = 0L
                var line = input.readLine()
                while (!line.isNullOrEmpty()) {
                    if (line.lowercase().startsWith("range: bytes=")) {
                        rangeStart = line.substring("range: bytes=".length).split("-")[0].trim()
                            .toLongOrNull() ?: 0L
                    }
                    line = input.readLine()
                }
                val out = socket.getOutputStream()
                val isHls =
                    currentUrl()?.contains(".m3u8") == true || currentUrl()?.contains("playlist") == true
                val contentType = getMimeType() ?: if (isHls) "audio/aac" else "audio/mpeg"
                val remoteBitrate = getBitrate()
                val bitrateHeader =
                    if (remoteBitrate != null) "X-Bitrate: $remoteBitrate\r\n" else ""
                val statusLine =
                    if (rangeStart > 0) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n"
                val rangeHeader =
                    if (rangeStart > 0) "Content-Range: bytes $rangeStart-/*\r\n" else ""
                val header =
                    statusLine + "Content-Type: $contentType\r\n" + "Accept-Ranges: bytes\r\n" + rangeHeader + bitrateHeader + "Connection: close\r\n\r\n"
                out.write(header.toByteArray())

                var lastReadPos = rangeStart
                val buffer = ByteArray(65536)
                while (isRunning() && sessionTag() == tag && !socket.isClosed) {
                    val read = cache.readDataNonBlocking(lastReadPos, buffer, 0, buffer.size)

                    if (read != null && read > 0) {
                        out.write(buffer, 0, read)
                        lastReadPos += read
                    } else if (read == -2) {
                        // Evicted - for HTTP client we just close or restart? 
                        // Usually we close to force a reconnect at new position
                        break
                    } else {
                        withContext(ioDispatcher) { withTimeoutOrNull(500.milliseconds) { dataSignal.first() } }
                    }
                }
            } catch (e: Exception) {
            } finally {
                runCatching { socket.close() }
            }
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (e: Exception) {
        }
        serverSocket = null
    }
}
