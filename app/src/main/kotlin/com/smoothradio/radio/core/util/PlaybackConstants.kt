package com.smoothradio.radio.core.util

object PlaybackConstants {
    const val HLS_SAFETY_BUFFER_MS = 6000L
    const val PROGRESSIVE_SAFETY_BUFFER_MS = 1000L
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
    const val BACK_SAFETY_BUFFER_MS = 1000L

    // Proxy Constants
    const val CACHE_PART_SIZE = 30 * 1024 * 1024L // 30MB = ~30 minutes of history at 128kbps
    const val PROXY_SCHEME = "proxy"
    const val PROXY_HOST = "smoothradio"
    const val PROXY_PATH = "/stream"
    const val PROXY_PARAM_BYTE_OFFSET = "byteOffset"
    const val PROXY_URL_BASE = "$PROXY_SCHEME://$PROXY_HOST$PROXY_PATH?$PROXY_PARAM_BYTE_OFFSET="

    // Proxy terminal error codes
    const val ERROR_UNREACHABLE = -3
    const val ERROR_EMPTY_STREAM = -4
    const val ERROR_CACHE_ERROR = -5

    // Proxy Analytics Error Codes
    const val ERROR_ANALYTICS_PROXY_STORAGE_INIT = 801
    const val ERROR_ANALYTICS_PROXY_STORAGE_WRITE = 802
}
