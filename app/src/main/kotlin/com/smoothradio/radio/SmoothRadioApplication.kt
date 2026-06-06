package com.smoothradio.radio

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.smoothradio.radio.core.logging.FirebaseCrashReportingTree
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class SmoothRadioApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var okHttpClient: OkHttpClient

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        setupLogging()

        setupFirebaseAnalytics()
    }

    private fun setupLogging() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            // In production, plant a tree that sends errors to Firebase
            Timber.plant(FirebaseCrashReportingTree())
        }
    }

    private fun setupFirebaseAnalytics() {
        val analytics = Firebase.analytics

        // Set user properties for better segmentation
        analytics.setUserProperty("android_version", Build.VERSION.RELEASE)
        analytics.setUserProperty("device_model", Build.MODEL)
        analytics.setUserProperty("app_version", BuildConfig.VERSION_NAME)
        analytics.setUserProperty("build_type", BuildConfig.BUILD_TYPE)
    }
}
