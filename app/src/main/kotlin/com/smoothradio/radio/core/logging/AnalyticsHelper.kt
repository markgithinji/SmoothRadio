package com.smoothradio.radio.core.logging

import android.os.Bundle
import com.google.firebase.analytics.analytics
import com.google.firebase.Firebase

object AnalyticsHelper {
    private val firebaseAnalytics = Firebase.analytics

    fun trackPlaybackEvent(
        event: String,
        stationId: Int? = null,
        additionalParams: Map<String, Any> = emptyMap()
    ) {
        try {
            val bundle = Bundle().apply {
                putString("event_type", event)
                putLong("timestamp", System.currentTimeMillis())

                stationId?.let { putString("station_id", it.toString()) }

                additionalParams.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Long -> putLong(key, value)
                        is Int -> putInt(key, value)
                        is Double -> putDouble(key, value)
                        is Boolean -> putBoolean(key, value)
                        else -> putString(key, value.toString())
                    }
                }
            }

            firebaseAnalytics.logEvent("playback_$event", bundle)
        } catch (e: Exception) {
        }
    }

    fun trackPlaybackError(
        errorCode: Int,
        errorMessage: String,
        stationId: Int? = null,
        additionalInfo: Map<String, String> = emptyMap()
    ) {
        try {
            val bundle = Bundle().apply {
                putString("error_type", "playback_error")
                putInt("error_code", errorCode)
                putString("error_message", errorMessage)
                putLong("timestamp", System.currentTimeMillis())

                stationId?.let { putString("station_id", it.toString()) }

                additionalInfo.forEach { (key, value) ->
                    putString(key, value)
                }
            }

            firebaseAnalytics.logEvent("playback_failure", bundle)
        } catch (e: Exception) {
        }
    }

    fun trackUserAction(
        action: String,
        stationId: Int? = null,
        additionalParams: Map<String, Any> = emptyMap()
    ) {
        try {
            val bundle = Bundle().apply {
                putString("action", action)
                putLong("timestamp", System.currentTimeMillis())
                stationId?.let { putString("station_id", it.toString()) }

                additionalParams.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Long -> putLong(key, value)
                        is Int -> putInt(key, value)
                        is Double -> putDouble(key, value)
                        is Boolean -> putBoolean(key, value)
                        else -> putString(key, value.toString())
                    }
                }
            }

            firebaseAnalytics.logEvent("user_action", bundle)
        } catch (e: Exception) {
        }
    }
}