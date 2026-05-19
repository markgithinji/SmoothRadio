package com.smoothradio.radio.core.util

import com.smoothradio.radio.BuildConfig

object AdConfig {
    val interstitialAdId: String = if (BuildConfig.DEBUG) {
        "ca-app-pub-9799428944156340/2070618771" // test interstitial
    } else {
        "ca-app-pub-9799428944156340/4028560879" // real interstitial
    }

    val bannerAdId: String = if (BuildConfig.DEBUG) {
        "ca-app-pub-3940256099942544/6300978111" // Universal Google test banner
    } else {
        "ca-app-pub-9799428944156340/4540584810" // real banner
    }
}
