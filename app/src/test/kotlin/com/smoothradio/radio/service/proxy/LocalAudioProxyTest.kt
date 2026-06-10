package com.smoothradio.radio.service.proxy

import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.service.util.proxy.DefaultAudioProxy
import com.smoothradio.radio.service.util.proxy.RollingDiskCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
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
        withTimeout(timeoutMs) {
            while (!condition()) {
                delay(intervalMs.milliseconds)
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
        // PART_SIZE is 1MB. Let's send 1.2MB.
        val largeDataSize = (1024 * 1024 * 1.2).toInt()
        val largeData = ByteArray(largeDataSize) { 0x01.toByte() }
        mockWebServer.enqueue(MockResponse().setBody(okio.Buffer().write(largeData)))

        proxy.start(mockWebServer.url("/large-stream").toString())
        
        awaitCondition { proxy.totalBytesWritten >= largeDataSize }

        // Give a tiny bit of extra time for the flush to finish if it happened exactly at the threshold
        delay(100)

        // Part 1 should be full (at least PART_SIZE)
        assertThat(proxy.totalBytesWritten).isAtLeast(RollingDiskCache.PART_SIZE)
        // Overflow should be in Part 2
        assertThat(proxy.totalBytesWritten).isGreaterThan(RollingDiskCache.PART_SIZE)
        assertThat(proxy.totalBytesDropped).isEqualTo(0) 
    }

    @Test
    fun `readData should return data from the buffer`() = runTest {
        val dummyData = ByteArray(1024) { i -> (i % 256).toByte() }
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
        // Force a drop by writing more than 3MB (TOTAL_CAPACITY_BYTES)
        val massiveData = ByteArray((1024 * 1024 * 3.2).toInt()) { 0x01.toByte() }
        mockWebServer.enqueue(MockResponse().setBody(okio.Buffer().write(massiveData)))

        proxy.start(mockWebServer.url("/massive").toString())
        
        awaitCondition(timeoutMs = 120000) { proxy.totalBytesDropped > 0 }

        assertThat(proxy.totalBytesDropped).isGreaterThan(0)
        
        // Try to read from the very beginning (which was dropped)
        val result = proxy.readData(tag = proxy.sessionTag, position = 0, buffer = ByteArray(100), offset = 0, length = 100)
        assertThat(result).isEqualTo(-2) // BufferEvictedException code
    }
}
