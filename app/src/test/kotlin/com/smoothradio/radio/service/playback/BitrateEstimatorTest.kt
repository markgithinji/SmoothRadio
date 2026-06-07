package com.smoothradio.radio.service.playback

import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.util.PlaybackConstants
import com.smoothradio.radio.service.util.playback.BitrateEstimator
import org.junit.Before
import org.junit.Test

class BitrateEstimatorTest {

    private lateinit var estimator: BitrateEstimator

    @Before
    fun setup() {
        estimator = BitrateEstimator()
    }

    @Test
    fun `when threshold not reached, return manifest bitrate if available`() {
        val result = estimator.calculate(
            totalBytesWritten = 1000,
            elapsedTimeMs = 1000,
            manifestBitrateKbps = 128.0,
            currentEstimation = PlaybackConstants.INITIAL_BITRATE_ESTIMATION
        )
        // 128 kbps = 16 bytes/ms
        assertThat(result).isEqualTo(16.0)
    }

    @Test
    fun `when threshold not reached and no manifest, return current estimation`() {
        val result = estimator.calculate(
            totalBytesWritten = 1000,
            elapsedTimeMs = 1000,
            manifestBitrateKbps = null,
            currentEstimation = PlaybackConstants.INITIAL_BITRATE_ESTIMATION
        )
        assertThat(result).isEqualTo(PlaybackConstants.INITIAL_BITRATE_ESTIMATION)
    }

    @Test
    fun `when threshold reached, use real-time bitrate`() {
        val result = estimator.calculate(
            totalBytesWritten = 40000, // 40000 bytes in 2001ms ~ 20 bytes/ms
            elapsedTimeMs = 2001,
            manifestBitrateKbps = null,
            currentEstimation = PlaybackConstants.INITIAL_BITRATE_ESTIMATION
        )
        assertThat(result).isWithin(0.1).of(20.0)
    }

    @Test
    fun `when manifest available and threshold reached, blend values`() {
        // Real: 20 bytes/ms
        // Manifest: 128kbps = 16 bytes/ms
        // Weight: 80% real, 20% manifest -> (20 * 0.8) + (16 * 0.2) = 16 + 3.2 = 19.2
        
        // Use a large enough elapsedTime to ensure we are well past threshold
        val elapsed = 5000L
        val bytes = 100000L // 20 bytes/ms
        val res = estimator.calculate(bytes, elapsed, 128.0, PlaybackConstants.INITIAL_BITRATE_ESTIMATION)
        
        assertThat(res).isWithin(0.01).of(19.2)
    }

    @Test
    fun `bitrate should be coerced within constants limits`() {
        // Very high real bitrate
        val result = estimator.calculate(
            totalBytesWritten = 1000000,
            elapsedTimeMs = 5000, // 200 bytes/ms
            manifestBitrateKbps = null,
            currentEstimation = PlaybackConstants.INITIAL_BITRATE_ESTIMATION
        )
        assertThat(result).isEqualTo(PlaybackConstants.MAX_BITRATE_BYTES_PER_MS)

        // Very low real bitrate
        val resultLow = estimator.calculate(
            totalBytesWritten = 1000,
            elapsedTimeMs = 5000, // 0.2 bytes/ms
            manifestBitrateKbps = null,
            currentEstimation = PlaybackConstants.INITIAL_BITRATE_ESTIMATION
        )
        assertThat(resultLow).isEqualTo(PlaybackConstants.MIN_BITRATE_BYTES_PER_MS)
    }
}
