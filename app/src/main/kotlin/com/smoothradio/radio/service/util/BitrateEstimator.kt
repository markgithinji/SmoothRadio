package com.smoothradio.radio.service.util

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
        if (elapsedTimeMs > 2000 && totalBytesWritten > 0) {
            val realTimeBitrate = (totalBytesWritten.toDouble() / elapsedTimeMs.toDouble()).coerceIn(4.0, 40.0)
            val manifestBitrate = manifestBitrateKbps?.let { it / 8.0 }

            return if (manifestBitrate != null) {
                // Blend with manifest hint, but favor reality (80% reality, 20% hint)
                (realTimeBitrate * 0.8) + (manifestBitrate * 0.2)
            } else {
                realTimeBitrate
            }
        } else if (manifestBitrateKbps != null) {
            return manifestBitrateKbps / 8.0
        }
        return currentEstimation
    }
}
