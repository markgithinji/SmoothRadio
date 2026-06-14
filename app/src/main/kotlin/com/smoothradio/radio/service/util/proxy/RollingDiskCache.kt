package com.smoothradio.radio.service.util.proxy

import com.smoothradio.radio.core.util.PlaybackConstants
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.TreeMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class RollingDiskCache(
    private val cacheDir: File,
    private val stateLock: ReentrantLock,
    private val dataCondition: java.util.concurrent.locks.Condition,
    private val maxPartSize: Long = PlaybackConstants.CACHE_PART_SIZE
) {
    private val memoryBuffer = FastMemoryBuffer(INITIAL_BURST_SIZE)
    private val metadataMap = TreeMap<Long, String>()

    @Volatile
    private var isDiskDisabled = false

    var sessionTag: String = ""
    var part1File: File? = null
        private set
    var part2File: File? = null
        private set

    @Volatile
    var totalBytesDropped = 0L
        private set

    @Volatile
    var totalBytesWritten = 0L
        private set

    @Volatile
    var totalBytesReceived = 0L

    fun reset(newTag: String) {
        stateLock.withLock {
            sessionTag = newTag
            isDiskDisabled = false // Reset fallback on new session
            cleanupLegacyFiles()
            part1File = File(cacheDir, "proxy_${sessionTag}_p1.mp3").apply { createNewFile() }
            part2File = File(cacheDir, "proxy_${sessionTag}_p2.mp3").apply { createNewFile() }
            totalBytesDropped = 0L
            totalBytesWritten = 0L
            totalBytesReceived = 0L
            metadataMap.clear()
            memoryBuffer.reset()
        }
    }

    fun appendData(tag: String, data: ByteArray, length: Int, offset: Int = 0, isHls: Boolean) {
        if (tag != sessionTag) return

        stateLock.withLock {
            runCatching {
                memoryBuffer.write(data, offset, length)
                totalBytesWritten += length
                if (!isHls) {
                    totalBytesReceived = totalBytesWritten
                }

                if (isDiskDisabled) {
                    // RAM FULL-PROOF: If disk is dead, keep RAM buffer small to avoid OOM
                    // We allow up to 1MB of "hot" data in RAM, then slide the window.
                    if (memoryBuffer.size() >= RAM_FALLBACK_LIMIT) {
                        val droppedFromRam = memoryBuffer.size().toLong()
                        totalBytesDropped += droppedFromRam
                        memoryBuffer.reset()
                        metadataMap.headMap(totalBytesDropped).clear()
                    }
                } else {
                    val threshold =
                        if (totalBytesWritten < 128 * 1024) INITIAL_BURST_SIZE else MEMORY_FLUSH_THRESHOLD
                    if (memoryBuffer.size() >= threshold) {
                        flushBufferToDiskInternal()
                    }
                }
                dataCondition.signalAll()
            }
        }
    }

    fun updateReceivedBytes(read: Int) {
        stateLock.withLock {
            totalBytesReceived += read
        }
    }

    fun flushBufferToDiskInternal() {
        if (memoryBuffer.size() == 0 || isDiskDisabled) return
        val p1 = part1File ?: return
        val p2 = part2File ?: return

        try {
            if (p1.length() < maxPartSize) {
                FileOutputStream(p1, true).use { memoryBuffer.writeTo(it) }
            } else if (p2.length() < maxPartSize) {
                FileOutputStream(p2, true).use { memoryBuffer.writeTo(it) }
            }

            if (p2.length() >= maxPartSize) {
                val p1Len = p1.length()
                if (p1.delete()) {
                    if (p2.renameTo(p1)) {
                        totalBytesDropped += p1Len
                        metadataMap.headMap(totalBytesDropped).clear()
                        p2.createNewFile()
                    } else {
                        // Manual copy fallback
                        try {
                            p2.inputStream().use { input ->
                                p1.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            totalBytesDropped += p1Len
                            metadataMap.headMap(totalBytesDropped).clear()
                            p2.delete()
                            p2.createNewFile()
                        } catch (e: Exception) {
                        }
                    }
                }
            }
            memoryBuffer.reset()
        } catch (e: java.io.IOException) {
            isDiskDisabled = true
            // Don't reset memoryBuffer yet, let appendData handle the windowing
        }
    }

    fun readData(
        tag: String,
        position: Long,
        buffer: ByteArray,
        offset: Int,
        length: Int,
        isRunning: () -> Boolean,
        terminalError: () -> Int
    ): Int {
        while (isRunning() && tag == sessionTag) {
            val error = terminalError()
            if (error != 0) return -3

            val result = readDataNonBlocking(position, buffer, offset, length)

            if (result != null) return result

            stateLock.withLock {
                if (isRunning() && tag == sessionTag) {
                    // Not enough data yet, wait
                    dataCondition.await(200, TimeUnit.MILLISECONDS)
                }
            }
        }
        return -1
    }

    fun readDataNonBlocking(position: Long, buffer: ByteArray, offset: Int, length: Int): Int? {
        return stateLock.withLock {
            val p1Size = part1File?.length() ?: 0L
            val p2Size = part2File?.length() ?: 0L
            val totalPhysicalSize = p1Size + p2Size

            val currentRelPos = position - totalBytesDropped

            if (currentRelPos < 0) {
                // Position is evicted from the buffer
                return@withLock -2 // Evicted
            }

            val minDataRequired =
                if (position == 0L) MIN_SNIFF_SIZE.toLong() else 1L // For server, 1 byte is enough to start
            val memoryPos = (currentRelPos - totalPhysicalSize).toInt()
            val memSize = memoryBuffer.size().toLong()

            val availableTotal = if (memoryPos < 0) {
                (totalPhysicalSize + memSize) - currentRelPos
            } else {
                memSize - memoryPos.toLong()
            }

            if (availableTotal < minDataRequired) {
                if (position == 0L) {
                    android.util.Log.d("SmoothRadio_Cache", "readData (pos=0): waiting for sniff data. available=$availableTotal, required=$minDataRequired")
                }
                return@withLock null
            }

            var totalRead = 0
            var tempRelPos = currentRelPos

            while (totalRead < length) {
                val remaining = length - totalRead
                val currentMemPos = (tempRelPos - totalPhysicalSize).toInt()

                if (currentMemPos >= 0) {
                    // Read from memory buffer
                    if (currentMemPos < memSize) {
                        val chunk = minOf(remaining.toLong(), memSize - currentMemPos).toInt()
                        System.arraycopy(
                            memoryBuffer.getInternalBuffer(),
                            currentMemPos,
                            buffer,
                            offset + totalRead,
                            chunk
                        )
                        totalRead += chunk
                        tempRelPos += chunk
                    } else break
                } else {
                    // Read from physical files
                    val file: File?
                    val fileOffset: Long
                    val fileSize: Long

                    if (tempRelPos < p1Size) {
                        file = part1File
                        fileOffset = tempRelPos
                        fileSize = p1Size
                    } else if (tempRelPos < totalPhysicalSize) {
                        file = part2File
                        fileOffset = tempRelPos - p1Size
                        fileSize = p2Size
                    } else {
                        file = null; fileOffset = 0; fileSize = 0
                    }

                    if (file != null && file.exists()) {
                        val chunkLimit = (fileSize - fileOffset).toInt()
                        val chunk = minOf(remaining, chunkLimit)
                        if (chunk > 0) {
                            try {
                                RandomAccessFile(file, "r").use { raf ->
                                    raf.seek(fileOffset)
                                    val read = raf.read(buffer, offset + totalRead, chunk)
                                    if (read > 0) {
                                        totalRead += read
                                        tempRelPos += read
                                    } else break
                                }
                            } catch (e: Exception) {
                                return@withLock -3
                            }
                        } else break
                    } else break
                }
            }
            if (totalRead > 0) totalRead else null
        }
    }

    fun storeMetadata(offset: Long, title: String) {
        stateLock.withLock {
            if (metadataMap.lastEntry()?.value != title) {
                metadataMap[offset] = title
            }
        }
    }

    fun getMetadataForOffset(offset: Long): String? {
        return stateLock.withLock {
            metadataMap.floorEntry(offset)?.value
        }
    }

    fun clearMetadata() {
        stateLock.withLock {
            metadataMap.clear()
        }
    }

    private fun cleanupLegacyFiles() {
        try {
            cacheDir.listFiles { f -> f.name.startsWith("proxy_") }?.forEach { it.delete() }
        } catch (e: Exception) {
        }
    }

    fun cleanup() {
        stateLock.withLock {
            flushBufferToDiskInternal()
            metadataMap.clear()
            part1File = null
            part2File = null
            sessionTag = ""
            cleanupLegacyFiles()
        }
    }

    private class FastMemoryBuffer(initialCapacity: Int) {
        private var buffer = ByteArray(initialCapacity)
        private var size = 0
        fun write(data: ByteArray, offset: Int, length: Int) {
            ensureCapacity(size + length)
            System.arraycopy(data, offset, buffer, size, length)
            size += length
        }

        private fun ensureCapacity(minCapacity: Int) {
            if (minCapacity > buffer.size) buffer =
                buffer.copyOf((buffer.size * 2).coerceAtLeast(minCapacity))
        }

        fun size() = size
        fun reset() {
            size = 0
        }

        fun getInternalBuffer() = buffer
        fun writeTo(out: OutputStream) {
            out.write(buffer, 0, size)
        }
    }

    companion object {
        const val MEMORY_FLUSH_THRESHOLD = 32 * 1024
        const val INITIAL_BURST_SIZE = 256 * 1024
        const val MIN_SNIFF_SIZE = 32 * 1024
        const val RAM_FALLBACK_LIMIT = 2 * 1024 * 1024 // 2MB limit when disk is full
    }
}
