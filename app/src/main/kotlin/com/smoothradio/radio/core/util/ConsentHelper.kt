package com.smoothradio.radio.core.util

import android.app.Activity
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentForm
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A helper class for managing user consent for personalized ads using the User Messaging Platform (UMP) SDK.
 *
 * This class handles:
 * - Requesting consent information updates.
 * - Loading and showing the consent form if required.
 * - Initializing the Mobile Ads SDK after consent is obtained (or if not required).
 *
 * @param activity The [Activity] context required for UMP and Mobile Ads SDK operations.
 */
class ConsentHelper(private val activity: Activity) {

    private var consentInformation: ConsentInformation? = null
    private val isMobileAdsInitializeCalled = AtomicBoolean(false)

    fun showConsentForm() {
        val params = ConsentRequestParameters.Builder().build()

        consentInformation = UserMessagingPlatform.getConsentInformation(activity)

        consentInformation?.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                    activity,
                    { loadAndShowError ->
                        if (loadAndShowError != null) {
                            // Consent gathering failed.
                        }
                        if (consentInformation?.canRequestAds() == true) {
                            initializeMobileAdsSdk()
                        }
                    }
                )
            },
            { requestConsentError ->
                // Consent gathering failed.
            }
        )

        if (consentInformation?.canRequestAds() == true) {
            initializeMobileAdsSdk()
        }
    }

    private fun initializeMobileAdsSdk() {
        if (isMobileAdsInitializeCalled.getAndSet(true)) return
        MobileAds.initialize(activity)
    }
}
