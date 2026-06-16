package com.smoothradio.radio.service.proxy

import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.util.PlaybackConstants
import com.smoothradio.radio.service.util.proxy.DefaultAudioProxy
import com.smoothradio.radio.service.util.proxy.ProxyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalAudioProxyTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockWebServer: MockWebServer
    private lateinit var proxy: DefaultAudioProxy

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        proxy = DefaultAudioProxy(
            cacheDir = tempFolder.newFolder(),
            ioDispatcher = Dispatchers.Default,
            okHttpClient = OkHttpClient(),
            maxPartSize = 256 * 1024L // Small part size for testing
        )
    }

    @After
    fun tearDown() {
        proxy.stop()
        mockWebServer.shutdown()
    }

    /**
     * Helper to wait for a condition. 
     * Uses a large virtual timeout to allow real-time background threads to progress.
     */
    private suspend fun awaitCondition(
        timeoutMs: Long = 60000,
        intervalMs: Long = 10,
        condition: () -> Boolean,
    ) {
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(timeoutMs) {
                while (!condition()) {
                    delay(intervalMs.milliseconds)
                }
            }
        }
    }

    @Test
    fun `proxy should download data and write to part files`() = runTest {
        // Create 100KB of dummy data
        val dummyData = ByteArray(1024 * 100) { 0x42.toByte() }
        mockWebServer.enqueue(MockResponse().setBody(okio.Buffer().write(dummyData)))

        proxy.start(mockWebServer.url("/stream").toString())
        
        // Polling condition
        awaitCondition { proxy.totalBytesWritten >= dummyData.size }

        assertThat(proxy.totalBytesWritten).isAtLeast(dummyData.size.toLong())
        assertThat(proxy.part1File?.exists()).isTrue()
    }

    @Test
    fun `proxy should rotate files when PART_SIZE is reached`() = runTest {
        // PART_SIZE is 256KB (set in setup). Let's send 600KB to ensure at least one rotation.
        val largeDataSize = 600 * 1024
        val largeData = ByteArray(largeDataSize) { 0x01.toByte() }
        mockWebServer.enqueue(MockResponse().setBody(okio.Buffer().write(largeData)))

        proxy.start(mockWebServer.url("/large-stream").toString())
        
        awaitCondition { proxy.totalBytesWritten >= largeDataSize }

        // Give a tiny bit of extra time for the flush to finish
        delay(100)

        // Rotation should have occurred since largeDataSize (600KB) > 2 * PART_SIZE (512KB)
        assertThat(proxy.totalBytesDropped).isGreaterThan(0)
        assertThat(proxy.totalBytesWritten).isEqualTo(largeDataSize.toLong())
    }

    @Test
    fun `readData from middle of buffer should support seeking`() = runTest {
        // Write 400KB of data
        val dataSize = 400 * 1024
        val dummyData = ByteArray(dataSize) { i -> (i % 256).toByte() }
        mockWebServer.enqueue(MockResponse().setBody(okio.Buffer().write(dummyData)))

        proxy.start(mockWebServer.url("/seek-test").toString())
        
        awaitCondition { proxy.totalBytesWritten >= dataSize }

        val buffer = ByteArray(1024)
        // Read from 100KB offset
        val offset = 100 * 1024L
        val bytesRead = proxy.readData(tag = proxy.sessionTag, position = offset, buffer = buffer, offset = 0, length = 1024)

        assertThat(bytesRead).isEqualTo(1024)
        assertThat(buffer[0]).isEqualTo((offset % 256).toByte())
    }

    @Test
    fun `readData should return data from the buffer`() = runTest {
        // Use data larger than MIN_SNIFF_SIZE (32KB) to allow reading from position 0
        val dummyData = ByteArray(1024 * 40) { i -> (i % 256).toByte() }
        mockWebServer.enqueue(MockResponse().setBody(okio.Buffer().write(dummyData)))

        proxy.start(mockWebServer.url("/read-test").toString())
        
        awaitCondition { proxy.totalBytesWritten >= dummyData.size }

        val buffer = ByteArray(512)
        // readData is blocking, but it's called on the test thread here.
        // It will return immediately because data is already in cache.
        val bytesRead = proxy.readData(tag = proxy.sessionTag, position = 0, buffer = buffer, offset = 0, length = 512)

        assertThat(bytesRead).isEqualTo(512)
        assertThat(buffer[0]).isEqualTo(0.toByte())
        assertThat(buffer[255]).isEqualTo(255.toByte())
        assertThat(buffer[256]).isEqualTo(0.toByte())
    }

    @Test
    fun `readData should return -2 when position is already dropped`() = runTest {
        // Trigger a drop by writing just over 2 * PART_SIZE (512KB)
        // PART_SIZE = 256KB (set in setup), so 600KB will fill Part 1, Part 2, and trigger a rotation.
        val dataSize = 600 * 1024
        val massiveData = ByteArray(dataSize) { 0x01.toByte() }
        mockWebServer.enqueue(MockResponse().setBody(okio.Buffer().write(massiveData)))

        proxy.start(mockWebServer.url("/massive").toString())
        
        awaitCondition(timeoutMs = 20000) { proxy.totalBytesDropped > 0 }

        assertThat(proxy.totalBytesDropped).isGreaterThan(0)
        
        // Try to read from the very beginning (which was dropped)
        val result = proxy.readData(tag = proxy.sessionTag, position = 0, buffer = ByteArray(100), offset = 0, length = 100)
        assertThat(result).isEqualTo(-2) // BufferEvictedException code
    }

    @Test
    fun `proxy should handle terminal error 403 from downloader`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(403))

        proxy.start(mockWebServer.url("/unreachable").toString())

        awaitCondition { proxy.terminalError != 0 }

        assertThat(proxy.terminalError).isEqualTo(PlaybackConstants.ERROR_UNREACHABLE)
        assertThat(proxy.proxyState.value).isEqualTo(ProxyState.Idle)
    }

    @Test
    fun `proxy should update state to Streaming and detect bitrate`() = runTest {
        mockWebServer.enqueue(MockResponse()
            .addHeader("Content-Type", "audio/mpeg")
            .addHeader("icy-br", "128")
            .setBody(okio.Buffer().write(ByteArray(1024))))

        proxy.start(mockWebServer.url("/stream").toString())

        awaitCondition { proxy.proxyState.value is ProxyState.Streaming }

        val state = proxy.proxyState.value as ProxyState.Streaming
        assertThat(state.mimeType).isEqualTo("audio/mpeg")
        assertThat(state.bitrate).isEqualTo("128")
        assertThat(proxy.detectedBitrateKbps).isEqualTo(128.0)
    }

    @Test
    fun `updateBitrateEstimation should update estimatedBytesPerMs`() = runTest {
        mockWebServer.enqueue(MockResponse()
            .addHeader("icy-br", "128")
            .setBody(okio.Buffer().write(ByteArray(1024))))

        proxy.start(mockWebServer.url("/bitrate").toString())
        awaitCondition { proxy.detectedBitrateKbps != null }

        proxy.updateBitrateEstimation()

        // 128 kbps / 8 = 16.0 bytes/ms
        assertThat(proxy.estimatedBytesPerMs).isWithin(0.1).of(16.0)
    }
}
