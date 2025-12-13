package com.smoothradio.radio

import android.app.Application
import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class SmoothRadioApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Setup logging
        setupLogging()

        // Setup Firebase Analytics
        setupFirebaseAnalytics()
    }

    private fun setupLogging() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            // In production, plant a tree that sends errors to Firebase
            Timber.plant(FirebaseCrashReportingTree())
        }

        Timber.d("Logging initialized - Debug mode: ${BuildConfig.DEBUG}")
    }

    private fun setupFirebaseAnalytics() {
        val analytics = Firebase.analytics

        // Set user properties for better segmentation
        analytics.setUserProperty("android_version", Build.VERSION.RELEASE)
        analytics.setUserProperty("device_model", Build.MODEL)
        analytics.setUserProperty("app_version", BuildConfig.VERSION_NAME)
        analytics.setUserProperty("build_type", BuildConfig.BUILD_TYPE)

        Timber.d("Firebase Analytics initialized")
    }
}
