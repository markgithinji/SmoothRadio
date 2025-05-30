package com.smoothradio.radio.core.util

import com.smoothradio.radio.BuildConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AdConfigTest {

    @Test
    fun interstitialAdId_matchesExpectedValueForCurrentBuildType() {
        val expected = if (BuildConfig.DEBUG) {
            "ca-app-pub-9799428944156340/2070618771" // test ID
        } else {
            "ca-app-pub-9799428944156340/4028560879" // real ID
        }

        assertThat(AdConfig.interstitialAdId).isEqualTo(expected)
    }

    @Test
    fun interstitialAdId_hasValidAdMobFormat() {
        val adId = AdConfig.interstitialAdId
        val pattern = Regex("^ca-app-pub-\\d{16}/\\d{10}$")
        assertThat(pattern.matches(adId)).isTrue()
    }
}
