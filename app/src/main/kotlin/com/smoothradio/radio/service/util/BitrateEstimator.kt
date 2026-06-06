package com.smoothradio.radio.service.util

import com.smoothradio.radio.core.util.PlaybackConstants
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BitrateEstimator @Inject constructor() {
    /**
     * Calculates the estimated bytes per millisecond based on real-world throughput
     * and optional manifest hints.
     */
    fun calculate(
        totalBytesWritten: Long,
        elapsedTimeMs: Long,
        manifestBitrateKbps: Double?,
        currentEstimation: Double
    ): Double {
        // Start trusting reality after just 2 seconds to correct for lying manifests
        if (elapsedTimeMs > PlaybackConstants.BITRATE_CALIBRATION_THRESHOLD_MS && totalBytesWritten > 0) {
            val realTimeBitrate = (totalBytesWritten.toDouble() / elapsedTimeMs.toDouble())
                .coerceIn(PlaybackConstants.MIN_BITRATE_BYTES_PER_MS, PlaybackConstants.MAX_BITRATE_BYTES_PER_MS)
            val manifestBitrate = manifestBitrateKbps?.let { it / PlaybackConstants.BITS_PER_BYTE }

            return if (manifestBitrate != null) {
                // Blend with manifest hint, but favor reality (80% reality, 20% hint)
                (realTimeBitrate * PlaybackConstants.BITRATE_REALITY_WEIGHT) + (manifestBitrate * PlaybackConstants.BITRATE_HINT_WEIGHT)
            } else {
                realTimeBitrate
            }
        } else if (manifestBitrateKbps != null) {
            return manifestBitrateKbps / PlaybackConstants.BITS_PER_BYTE
        }
        return currentEstimation
    }
}
