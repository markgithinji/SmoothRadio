package com.smoothradio.radio.core.domain.usecase

import com.smoothradio.radio.LoggingHelper
import com.smoothradio.radio.core.domain.repository.AdSettingsRepository
import javax.inject.Inject

class CanShowAdUseCase @Inject constructor(
    private val adSettingsRepository: AdSettingsRepository
) {
    suspend operator fun invoke(): Boolean {
        val lastShowTime = adSettingsRepository.getLastAdShowTime()
        val currentHour = getCurrentHour()
        val lastHour = adSettingsRepository.getLastAdHour()
        val currentCount = adSettingsRepository.getAdShowCount()

        val effectiveCount = if (currentHour != lastHour) 0 else currentCount
        val timeSinceLastAd = System.currentTimeMillis() - lastShowTime
        val minutesSinceLastAd = timeSinceLastAd / (1000 * 60)

        val canShow =
            minutesSinceLastAd >= AD_SHOW_INTERVAL_MINUTES && effectiveCount < MAX_ADS_PER_HOUR

        LoggingHelper.d(
            message = "canShow=$canShow | ${minutesSinceLastAd}m since last ad | count: $effectiveCount/$MAX_ADS_PER_HOUR",
            tag = TAG
        )

        return canShow
    }

    private fun getCurrentHour(): Long {
        return System.currentTimeMillis() / (1000 * 60 * 60)
    }

    companion object {
        private const val TAG = "CanShowAdUseCase"
        private const val AD_SHOW_INTERVAL_MINUTES = 2
        private const val MAX_ADS_PER_HOUR = 3
    }
}