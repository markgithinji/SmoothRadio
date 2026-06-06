package com.smoothradio.radio.core.util

object PlaybackConstants {
    const val HLS_SAFETY_BUFFER_MS = 12000L
    const val PROGRESSIVE_SAFETY_BUFFER_MS = 2000L
    const val BUSY_PROGRESS_UPDATE_DELAY_MS = 100L
    const val IDLE_PROGRESS_UPDATE_DELAY_MS = 1000L
    const val INITIAL_BITRATE_ESTIMATION = 16.0
    const val BITRATE_CALIBRATION_THRESHOLD_MS = 2000L
    const val MIN_BITRATE_BYTES_PER_MS = 4.0
    const val MAX_BITRATE_BYTES_PER_MS = 40.0
    const val BITS_PER_BYTE = 8.0
    const val BITRATE_REALITY_WEIGHT = 0.8
    const val BITRATE_HINT_WEIGHT = 0.2
    const val PROGRESS_TARGET_MS = 2000.0
    const val PROGRESS_BASELINE = 0.05f
    const val PROGRESS_SCALE = 0.95f
    const val LIVE_OFFSET_TARGET_MS = 2000L
    const val SEEK_INCREMENT_MS = 10000L

    // Proxy terminal error codes
    const val ERROR_UNREACHABLE = -3
    const val ERROR_EMPTY_STREAM = -4
    const val ERROR_CACHE_ERROR = -5
}
