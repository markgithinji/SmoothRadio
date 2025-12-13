package com.smoothradio.radio

import timber.log.Timber

object LoggingHelper {

    private const val TAG = "RadioApp"

    // Info level logging
    fun i(message: String, vararg args: Any) {
        Timber.tag(TAG).i(message, *args)
    }

    // Debug level logging
    fun d(message: String, vararg args: Any) {
        Timber.tag(TAG).d(message, *args)
    }

    // Warning level logging
    fun w(message: String, vararg args: Any) {
        Timber.tag(TAG).w(message, *args)
    }

    // Error level logging
    fun e(message: String, throwable: Throwable? = null, vararg args: Any) {
        Timber.tag(TAG).e(throwable, message, *args)
    }

    // Playback specific logging - now accepts Int for stationId
    fun playback(message: String, stationId: Int? = null) {
        val stationInfo = stationId?.let { " - Station: $it" } ?: ""
        Timber.tag("Playback").d("$message$stationInfo")
    }

    // Network specific logging
    fun network(message: String) {
        Timber.tag("Network").d(message)
    }

    // Audio specific logging
    fun audio(message: String) {
        Timber.tag("Audio").d(message)
    }
}