package com.smoothradio.radio.core.logging

// FirebaseCrashReportingTree.kt
import android.os.Bundle
import android.util.Log
import timber.log.Timber
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.Firebase

/**
 * Timber tree that sends errors and warnings to Firebase Crashlytics
 * and logs important events to Firebase Analytics
 */
class FirebaseCrashReportingTree : Timber.Tree() {

    private val firebaseAnalytics: FirebaseAnalytics by lazy { Firebase.analytics }
    private val crashlytics: FirebaseCrashlytics by lazy { FirebaseCrashlytics.getInstance() }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority == Log.ERROR || priority == Log.WARN) {
            // Send errors and warnings to Crashlytics
            crashlytics.log("$tag: $message")

            if (t != null) {
                crashlytics.recordException(t)
            } else {
                // Log non-exception errors as a custom exception for tracking
                val exception = RuntimeException("Log: $message")
                crashlytics.recordException(exception)
            }

            // Also log important errors to Analytics as custom events
            if (priority == Log.ERROR) {
                logErrorToAnalytics(message, tag)
            }
        }
    }

    private fun logErrorToAnalytics(message: String, tag: String?) {
        val bundle = Bundle().apply {
            putString("error_message", message)
            putString("error_tag", tag)
            putLong("timestamp", System.currentTimeMillis())
        }
        firebaseAnalytics.logEvent("app_error", bundle)
    }
}