package com.smoothradio.radio.core.util

import com.smoothradio.radio.BuildConfig

object AdConfig {
    val interstitialAdId: String
        get() = if (BuildConfig.DEBUG) {
            "cca-app-pub-9799428944156340/2070618771" // test interstitial
        } else {
            "ca-app-pub-9799428944156340/4028560879" // real interstitial
        }
}
