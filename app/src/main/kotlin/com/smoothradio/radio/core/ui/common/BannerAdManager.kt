package com.smoothradio.radio.core.ui.common

import android.content.Context
import android.view.ViewGroup
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.smoothradio.radio.core.util.AdConfig

/**
 * A singleton manager to hold and preserve the state of a banner ad.
 * This prevents the ad from reloading every time the screen is navigated away from and back to.
 */
object BannerAdManager {
    private var cachedAdView: AdView? = null

    fun getOrCreateAdView(context: Context): AdView {
        return cachedAdView ?: AdView(context).apply {
            adUnitId = AdConfig.bannerAdId
            setAdSize(AdSize.BANNER)
            loadAd(AdRequest.Builder().build())
            cachedAdView = this
        }
    }

    /**
     * Call this when the ad view should be detached from its current parent
     * to avoid "The specified child already has a parent" error when re-using.
     */
    fun detachAdView() {
        cachedAdView?.let { adView ->
            (adView.parent as? ViewGroup)?.removeView(adView)
        }
    }
}
