package com.smoothradio.radio.core.domain.usecase

import com.smoothradio.radio.core.domain.repository.AdSettingsRepository
import com.smoothradio.radio.core.logging.LoggingHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CanShowAdUseCase @Inject constructor(
    private val adSettingsRepository: AdSettingsRepository
) {

    operator fun invoke(): Flow<Boolean> = adSettingsRepository.adSettings.map { settings ->
        val lastShowTime = settings.lastAdShowTime
        val currentHour = getCurrentHour()
        val lastHour = settings.lastAdHour
        val currentCount = settings.adShowCount

        val intervalMinutes = settings.adShowIntervalMinutes
        val maxPerHour = settings.maxAdsPerHour

        // Reset count to 0 if a new hour has started since the last ad was shown.
        // This ensures we don't carry over ad counts from the previous hour,
        val effectiveCount = if (currentHour != lastHour) 0L else currentCount
        val timeSinceLastAd = System.currentTimeMillis() - lastShowTime
        val minutesSinceLastAd = timeSinceLastAd / (1000 * 60)

        val canShow = minutesSinceLastAd >= intervalMinutes && effectiveCount < maxPerHour

        LoggingHelper.d(
            message = "canShow=$canShow | ${minutesSinceLastAd}m/$intervalMinutes since last ad | count: $effectiveCount/$maxPerHour",
            tag = TAG
        )

        canShow
    }

    private fun getCurrentHour(): Long {
        return System.currentTimeMillis() / (1000 * 60 * 60)
    }

    companion object {
        private const val TAG = "CanShowAdUseCase"
    }
}
