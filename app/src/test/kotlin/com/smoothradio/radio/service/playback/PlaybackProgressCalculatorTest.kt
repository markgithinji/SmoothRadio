package com.smoothradio.radio.service.playback

import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.util.PlaybackConstants
import com.smoothradio.radio.service.util.playback.PlaybackProgressCalculator
import org.junit.Before
import org.junit.Test

class PlaybackProgressCalculatorTest {

    private lateinit var calculator: PlaybackProgressCalculator

    @Before
    fun setup() {
        calculator = PlaybackProgressCalculator()
    }

    @Test
    fun `calculate should return correct snapshot for progressive stream`() {
        val result = calculator.calculate(
            currentPosition = 5000,
            totalBytesWritten = 160000, // 10000ms at 16 bytes/ms
            totalBytesDropped = 32000,  // 2000ms at 16 bytes/ms
            totalBytesReceived = 160000,
            estimatedBytesPerMs = 16.0,
            isHls = false,
            totalCapacityBytes = 48000, // 3000ms at 16 bytes/ms
            isBuffering = false
        )

        assertThat(result.position).isEqualTo(5000)
        assertThat(result.minPosition).isEqualTo(2000)
        
        // duration = droppedDur + bufferCapacityMs = 2000 + 3000 = 5000
        // Wait, why is duration only 5000? 
        // totalCapacityBytes = 48000 -> 3000ms. 
        // Total loaded is 10000ms. 
        // Logic says displayDur = droppedDur + bufferCapacityMs.
        // This means the seek bar shows a fixed window starting from dropped position.
        assertThat(result.duration).isEqualTo(5000)

        // loadedPosition = (loadedDur - safetyBuffer) = 10000 - 1000 = 9000.
        // Coerced at least droppedDur (2000).
        assertThat(result.loadedPosition).isEqualTo(9000)
        assertThat(result.loadingProgress).isEqualTo(1.0f)
    }

    @Test
    fun `calculate should use HLS safety buffer when specified`() {
        val result = calculator.calculate(
            currentPosition = 0,
            totalBytesWritten = 320000, // 20000ms at 16 bytes/ms
            totalBytesDropped = 0,
            totalBytesReceived = 320000,
            estimatedBytesPerMs = 16.0,
            isHls = true,
            totalCapacityBytes = 160000,
            isBuffering = false
        )

        // HLS safety buffer is 12000L
        // loadedPosition = 20000 - 12000 = 8000
        assertThat(result.loadedPosition).isEqualTo(20000L - PlaybackConstants.HLS_SAFETY_BUFFER_MS)
    }

    @Test
    fun `loading progress should reflect buffering state`() {
        val estimatedBytesPerMs = 16.0
        
        // Halfway there (target is 2000ms, we provide 1000ms worth of bytes)
        val result = calculator.calculate(
            currentPosition = 0,
            totalBytesWritten = 16000,
            totalBytesDropped = 0,
            totalBytesReceived = 16000,
            estimatedBytesPerMs = estimatedBytesPerMs,
            isHls = false,
            totalCapacityBytes = 100000,
            isBuffering = true
        )

        // progress = 1000ms / 2000ms = 0.5
        // loadingProgress = baseline + (0.5 * scale)
        val expectedProgress = PlaybackConstants.PROGRESS_BASELINE + (0.5f * PlaybackConstants.PROGRESS_SCALE)
        assertThat(result.loadingProgress).isWithin(0.01f).of(expectedProgress)
    }
    
    @Test
    fun `negative position should be coerced to zero`() {
        val result = calculator.calculate(
            currentPosition = -100,
            totalBytesWritten = 0,
            totalBytesDropped = 0,
            totalBytesReceived = 0,
            estimatedBytesPerMs = 16.0,
            isHls = false,
            totalCapacityBytes = 100000,
            isBuffering = false
        )
        assertThat(result.position).isEqualTo(0)
    }
}
