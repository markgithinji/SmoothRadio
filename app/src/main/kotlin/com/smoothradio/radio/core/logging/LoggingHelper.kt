package com.smoothradio.radio.core.logging

import timber.log.Timber

object LoggingHelper {

    private const val DEFAULT_TAG = "RadioApp"

    fun i(message: String, tag: String = DEFAULT_TAG, vararg args: Any) {
        Timber.tag(tag).i(message, *args)
    }

    fun d(message: String, tag: String = DEFAULT_TAG, vararg args: Any) {
        Timber.tag(tag).d(message, *args)
    }

    fun w(message: String, tag: String = DEFAULT_TAG, vararg args: Any) {
        Timber.tag(tag).w(message, *args)
    }

    fun e(message: String, tag: String = DEFAULT_TAG, throwable: Throwable? = null, vararg args: Any) {
        Timber.tag(tag).e(throwable, message, *args)
    }
}