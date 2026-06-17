package com.smoothradio.radio.service.util.proxy

import androidx.media3.common.MimeTypes
import com.smoothradio.radio.core.util.PlaybackConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

interface StreamDownloader {
    suspend fun download(url: String, tag: String)
}

class ProgressiveDownloader(
    private val okHttpClient: OkHttpClient,
    private val cache: RollingDiskCache,
    private val isRunning: () -> Boolean,
    private val sessionTag: () -> String,
    private val onStateUpdate: (ProxyState) -> Unit,
    private val onTerminalError: (Int) -> Unit,
    private val onBitrateDetected: (Double) -> Unit
) : StreamDownloader {

    override suspend fun download(url: String, tag: String): Unit = withContext(Dispatchers.IO) {
        var retryCount = 0
        var hasReceivedAnyData = false

        while (isRunning() && sessionTag() == tag) {
            val maxAllowedRetries = if (hasReceivedAnyData) 15 else 3
            if (retryCount >= maxAllowedRetries) {
                onTerminalError(PlaybackConstants.ERROR_UNREACHABLE)
                return@withContext
            }

            try {
                val timeout =
                    if (retryCount > 0) RETRY_READ_TIMEOUT_SEC else DEFAULT_READ_TIMEOUT_SEC
                
                if (retryCount > 0) {
                    onStateUpdate(ProxyState.Retrying)
                }

                val useMetadata = true
                val response =
                    executeStreamRequest(url, requestMetadata = true, timeoutSeconds = timeout)

                if (response != null && (response.code == 401 || response.code == 403)) {
                    onTerminalError(PlaybackConstants.ERROR_UNREACHABLE)
                    return@withContext
                }

                response?.use { res ->
                    if (!res.isSuccessful) throw Exception("HTTP ${res.code}")
                    retryCount = 0

                    val contentType = res.header("Content-Type")
                    val bitrateStr = res.header("icy-br")
                    onStateUpdate(ProxyState.Streaming(contentType, bitrateStr))
                    bitrateStr?.toDoubleOrNull()?.let { onBitrateDetected(it) }

                    val inputStream = res.body.byteStream()
                    val metaint =
                        if (useMetadata) res.header("icy-metaint")?.toIntOrNull() ?: -1 else -1

                    if (metaint > 0) {
                        var bytesUntilMetadata = metaint
                        while (isRunning() && sessionTag() == tag) {
                            if (bytesUntilMetadata > 0) {
                                val buf = ByteArray(minOf(bytesUntilMetadata, 8192))
                                val read = inputStream.read(buf)
                                if (read == -1) break
                                hasReceivedAnyData = true
                                retryCount = 0 // Reset on data
                                cache.appendData(tag, buf, read, isHls = false)
                                bytesUntilMetadata -= read
                            } else {
                                val n = inputStream.read()
                                if (n == -1) break
                                if (n > 0) {
                                    val metaLen = n * 16
                                    val metaBuf = ByteArray(metaLen)
                                    var metaRead = 0
                                    while (metaRead < metaLen) {
                                        val r =
                                            inputStream.read(metaBuf, metaRead, metaLen - metaRead)
                                        if (r == -1) break
                                        metaRead += r
                                    }
                                    val metadata = String(metaBuf, 0, metaRead, Charsets.UTF_8)
                                    parseIcyMetadata(metadata)?.let { title ->
                                        cache.storeMetadata(cache.totalBytesWritten, title)
                                    }
                                }
                                bytesUntilMetadata = metaint
                            }
                        }
                    } else {
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (isRunning() && sessionTag() == tag) {
                            bytesRead = inputStream.read(buffer)
                            if (bytesRead == -1) break
                            hasReceivedAnyData = true
                            retryCount = 0 // Reset on data
                            cache.appendData(tag, buffer, bytesRead, isHls = false)
                        }
                    }
                }
                if (isRunning() && sessionTag() == tag) throw Exception("Connection lost")
            } catch (ignored: Exception) {
                if (!isRunning() || sessionTag() != tag) break
                retryCount++
                delay(RETRY_DELAY_MS.milliseconds)
            }
        }
    }

    private fun executeStreamRequest(
        url: String,
        requestMetadata: Boolean,
        timeoutSeconds: Int
    ): Response? {
        val client = okHttpClient.newBuilder()
            .readTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .connectTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .build()
        val requestBuilder = Request.Builder().url(url)
            .addHeader("User-Agent", "ExoPlayer/2.18.5")
            .addHeader("Accept", "*/*")
            .addHeader("Connection", "keep-alive")
        if (requestMetadata) requestBuilder.addHeader("Icy-MetaData", "1")
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
                val title = Regex("Title=\"(.*?)\"").find(rawTitle)?.groupValues?.get(1)
                val artist = Regex("Artist1=\"(.*?)\"").find(rawTitle)?.groupValues?.get(1)
                if (title != null && artist != null) return "$title - $artist"
                return title
            } catch (ignored: Exception) {
            }
        }
        return rawTitle
    }

    companion object {
        private const val DEFAULT_READ_TIMEOUT_SEC = 10
        private const val RETRY_READ_TIMEOUT_SEC = 15
        private const val RETRY_DELAY_MS = 2000L
    }
}

class HlsDownloader(
    private val okHttpClient: OkHttpClient,
    private val cache: RollingDiskCache,
    private val scope: CoroutineScope,
    private val isRunning: () -> Boolean,
    private val sessionTag: () -> String,
    private val onStateUpdate: (ProxyState) -> Unit,
    private val onBitrateDetected: (Double) -> Unit,
    private val onTerminalError: (Int) -> Unit
) : StreamDownloader {

    override suspend fun download(url: String, tag: String): Unit = withContext(Dispatchers.IO) {
        val downloadedSegments = mutableSetOf<String>()
        val baseUrl = url.substring(0, url.lastIndexOf("/") + 1)
        var retryCount = 0
        var hasReceivedAnyData = false

        while (isRunning() && sessionTag() == tag) {
            val maxAllowedRetries = if (hasReceivedAnyData) 15 else 3
            if (retryCount >= maxAllowedRetries) {
                onTerminalError(PlaybackConstants.ERROR_UNREACHABLE)
                return@withContext
            }

            try {
                if (retryCount > 0) {
                    onStateUpdate(ProxyState.Retrying)
                }

                val client = okHttpClient.newBuilder()
                    .connectTimeout(RETRY_READ_TIMEOUT_SEC.toLong(), TimeUnit.SECONDS)
                    .readTimeout(RETRY_READ_TIMEOUT_SEC.toLong(), TimeUnit.SECONDS)
                    .build()

                val request =
                    Request.Builder().url(url).addHeader("User-Agent", "Mozilla/5.0").build()
                val playlistText = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        if (response.code == 401 || response.code == 403) {
                            onTerminalError(PlaybackConstants.ERROR_UNREACHABLE)
                            return@withContext
                        }
                        throw Exception("HTTP ${response.code}")
                    }
                    response.body.string()
                }
                
                // If we successfully get the playlist, we are back in streaming mode (conceptually)
                onStateUpdate(ProxyState.Streaming(MimeTypes.AUDIO_AAC, null))
                retryCount = 0

                if (playlistText.isEmpty()) {
                    delay(HLS_PLAYLIST_RETRY_DELAY_MS.milliseconds); continue
                }

                val lines = playlistText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (playlistText.contains("#EXT-X-STREAM-INF")) {
                    val variantLines = lines.mapIndexedNotNull { index, line ->
                        if (line.startsWith("#EXT-X-STREAM-INF")) {
                            val vUrl = lines.getOrNull(index + 1)
                            if (vUrl != null && !vUrl.startsWith("#")) line to vUrl else null
                        } else null
                    }
                    val bestVariant = variantLines.mapNotNull { (info, vUrl) ->
                        val bandwidth = Regex("BANDWIDTH=(\\d+)").find(info)?.groupValues?.get(1)
                            ?.toLongOrNull()
                        if (bandwidth != null) Triple(bandwidth, info, vUrl) else null
                    }.maxByOrNull { it.first }

                    if (bestVariant != null) {
                        onBitrateDetected(bestVariant.first.toDouble() / 1000.0)
                        val fullUrl =
                            if (bestVariant.third.startsWith("http")) bestVariant.third else baseUrl + bestVariant.third
                        download(fullUrl, tag)
                        return@withContext
                    }
                }

                val allSegments = lines.filter { !it.startsWith("#") }
                val newSegments = allSegments.filter { !downloadedSegments.contains(it) }

                val deferreds = newSegments.take(MAX_PARALLEL_DOWNLOADS).map { segmentPath ->
                    val segmentUrl =
                        if (segmentPath.startsWith("http")) segmentPath else baseUrl + segmentPath
                    scope.async {
                        runCatching {
                            val segRequest = Request.Builder().url(segmentUrl)
                                .addHeader("User-Agent", "Mozilla/5.0").build()
                            okHttpClient.newCall(segRequest).execute().use { response ->
                                if (!response.isSuccessful) return@use byteArrayOf()
                                val out = ByteArrayOutputStream()
                                val inputStream = response.body.byteStream()
                                val buffer = ByteArray(8192)
                                var read: Int
                                while (isRunning() && sessionTag() == tag) {
                                    read = inputStream.read(buffer)
                                    if (read == -1) break
                                    out.write(buffer, 0, read)
                                    cache.updateReceivedBytes(read)
                                }
                                out.toByteArray()
                            }
                        }.getOrDefault(byteArrayOf())
                    }
                }

                val segmentDataList = deferreds.awaitAll()
                newSegments.take(MAX_PARALLEL_DOWNLOADS).forEachIndexed { index, segmentPath ->
                    if (!isRunning() || sessionTag() != tag) return@forEachIndexed
                    val data = segmentDataList[index]
                    if (data.isNotEmpty()) {
                        retryCount = 0 // Reset retry count on any successful data
                        
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
                            }
                        }

                        cache.appendData(
                            tag,
                            data,
                            data.size - headerOffset,
                            headerOffset,
                            isHls = true
                        )
                        hasReceivedAnyData = true
                        downloadedSegments.add(segmentPath)
                    }
                }
                if (newSegments.isNotEmpty()) delay(HLS_SEGMENT_DOWNLOAD_DELAY_MS.milliseconds) else delay(
                    HLS_EMPTY_PLAYLIST_DELAY_MS.milliseconds
                )
            } catch (ignored: Exception) {
                if (!isRunning() || sessionTag() != tag) break
                retryCount++
                delay(RETRY_DELAY_MS.milliseconds)
            }
        }
    }

    companion object {
        private const val MAX_PARALLEL_DOWNLOADS = 6
        private const val RETRY_READ_TIMEOUT_SEC = 15
        private const val RETRY_DELAY_MS = 2000L
        private const val HLS_PLAYLIST_RETRY_DELAY_MS = 2000L
        private const val HLS_SEGMENT_DOWNLOAD_DELAY_MS = 700L
        private const val HLS_EMPTY_PLAYLIST_DELAY_MS = 4000L
    }
}
