package com.smoothradio.radio.core.domain.usecase

import com.smoothradio.radio.core.logging.LoggingHelper
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

        val intervalMinutes = adSettingsRepository.getAdShowIntervalMinutes()
        val maxPerHour = adSettingsRepository.getMaxAdsPerHour()

        // Reset count to 0 if a new hour has started since the last ad was shown.
        // This ensures we don't carry over ad counts from the previous hour,
        val effectiveCount = if (currentHour != lastHour) 0 else currentCount
        val timeSinceLastAd = System.currentTimeMillis() - lastShowTime
        val minutesSinceLastAd = timeSinceLastAd / (1000 * 60)

        val canShow = minutesSinceLastAd >= intervalMinutes && effectiveCount < maxPerHour

        LoggingHelper.d(
            message = "canShow=$canShow | ${minutesSinceLastAd}m/$intervalMinutes since last ad | count: $effectiveCount/$maxPerHour",
            tag = TAG
        )

        return canShow
    }

    private fun getCurrentHour(): Long {
        return System.currentTimeMillis() / (1000 * 60 * 60)
    }

    companion object {
        private const val TAG = "CanShowAdUseCase"
    }
}