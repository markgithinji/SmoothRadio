package com.smoothradio.radio.service.proxy

import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.service.util.proxy.RollingDiskCache
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.locks.ReentrantLock

class RollingDiskCacheTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var cacheDir: File
    private lateinit var stateLock: ReentrantLock
    private lateinit var dataCondition: java.util.concurrent.locks.Condition
    private lateinit var cache: RollingDiskCache

    private val partSize = 1024L * 100 // 100KB for testing

    @Before
    fun setup() {
        cacheDir = tempFolder.newFolder()
        stateLock = ReentrantLock()
        dataCondition = stateLock.newCondition()
        cache = RollingDiskCache(cacheDir, stateLock, dataCondition, partSize)
        cache.reset("test-tag")
    }

    @Test
    fun `initial state is correct`() {
        assertThat(cache.sessionTag).isEqualTo("test-tag")
        assertThat(cache.totalBytesWritten).isEqualTo(0L)
        assertThat(cache.totalBytesDropped).isEqualTo(0L)
        assertThat(cache.part1File?.exists()).isTrue()
        assertThat(cache.part2File?.exists()).isTrue()
    }

    @Test
    fun `appendData updates bytes written`() {
        val data = ByteArray(1024) { 0x1 }
        cache.appendData("test-tag", data, data.size, isHls = false)
        
        assertThat(cache.totalBytesWritten).isEqualTo(1024L)
        assertThat(cache.totalBytesReceived).isEqualTo(1024L)
    }

    @Test
    fun `readData from memory works`() {
        val data = ByteArray(1024) { i -> (i % 256).toByte() }
        // MIN_SNIFF_SIZE is 32KB, so we need to write enough to allow reading from pos 0
        val burstData = ByteArray(32 * 1024) { 0 }
        cache.appendData("test-tag", burstData, burstData.size, isHls = false)
        cache.appendData("test-tag", data, data.size, isHls = false)

        val buffer = ByteArray(1024)
        val read = cache.readDataNonBlocking(32 * 1024L, buffer, 0, 1024)

        assertThat(read).isEqualTo(1024)
        assertThat(buffer[0]).isEqualTo(0.toByte())
        assertThat(buffer[255]).isEqualTo(255.toByte())
    }

    @Test
    fun `flushBufferToDisk writes memory to files`() {
        // Write enough to trigger a flush (MEMORY_FLUSH_THRESHOLD = 32KB)
        val data = ByteArray(40 * 1024) { 0x2 }
        cache.appendData("test-tag", data, data.size, isHls = false)
        
        // flushBufferToDiskInternal is internal, but appendData calls it if threshold reached
        // Actually, it calls it in appendData.
        
        assertThat(cache.part1File?.length()).isGreaterThan(0L)
    }

    @Test
    fun `rotation occurs when part size is exceeded`() {
        // Part size is 100KB.
        // We write 250KB total.
        // 1. Initial burst (256KB) -> Wait, INITIAL_BURST_SIZE is 256KB.
        // Let's use a custom small INITIAL_BURST_SIZE if I could, but it's constant.
        // I'll just write a lot of data.
        
        val chunk = ByteArray(50 * 1024) { 0x3 } // 50KB
        
        // Write 300KB
        repeat(6) {
            cache.appendData("test-tag", chunk, chunk.size, isHls = false)
        }
        
        // totalBytesWritten = 300KB. 
        // part1 = 100KB, part2 = 100KB. 
        // 3rd 100KB should trigger rotation.
        // totalBytesDropped should be 100KB.
        
        assertThat(cache.totalBytesDropped).isAtLeast(100 * 1024L)
        assertThat(cache.totalBytesWritten).isEqualTo(300 * 1024L)
    }

    @Test
    fun `readData returns -2 when position is evicted`() {
        val chunk = ByteArray(50 * 1024) { 0x4 }
        repeat(6) {
            cache.appendData("test-tag", chunk, chunk.size, isHls = false)
        }
        
        assertThat(cache.totalBytesDropped).isGreaterThan(0L)
        
        val result = cache.readDataNonBlocking(0L, ByteArray(100), 0, 100)
        assertThat(result).isEqualTo(-2)
    }

    @Test
    fun `metadata storage and retrieval works`() {
        cache.storeMetadata(100L, "Song 1")
        cache.storeMetadata(500L, "Song 2")
        
        assertThat(cache.getMetadataForOffset(150L)).isEqualTo("Song 1")
        assertThat(cache.getMetadataForOffset(600L)).isEqualTo("Song 2")
        assertThat(cache.getMetadataForOffset(50L)).isNull()
    }

    @Test
    fun `reset clears state`() {
        cache.appendData("test-tag", ByteArray(1024), 1024, isHls = false)
        cache.storeMetadata(100L, "Song 1")
        
        cache.reset("new-tag")
        
        assertThat(cache.sessionTag).isEqualTo("new-tag")
        assertThat(cache.totalBytesWritten).isEqualTo(0L)
        assertThat(cache.getMetadataForOffset(100L)).isNull()
    }
}
