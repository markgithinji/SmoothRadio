package com.smoothradio.radio.core.domain.usecase

import com.smoothradio.radio.core.domain.repository.AdSettingsRepository
import com.smoothradio.radio.core.logging.LoggingHelper
import javax.inject.Inject

class RecordAdShownUseCase @Inject constructor(
    private val adSettingsRepository: AdSettingsRepository
) {

    suspend operator fun invoke() {
        val currentTime = System.currentTimeMillis()
        val currentHour = getCurrentHour()
        val settings = adSettingsRepository.getAdSettings()

        val lastHour = settings.lastAdHour
        val currentCount = settings.adShowCount

        val isNewHour = currentHour != lastHour
        val newCount = if (isNewHour) {
            1L
        } else {
            currentCount + 1
        }

        LoggingHelper.d(
            message = "Recording ad shown - currentHour: $currentHour, lastHour: $lastHour, previousCount: $currentCount, newCount: $newCount, isNewHour: $isNewHour",
            tag = TAG
        )

        adSettingsRepository.updateAdDataWithCount(currentTime, currentHour, newCount)
    }

    private fun getCurrentHour(): Long {
        return System.currentTimeMillis() / (1000 * 60 * 60)
    }

    companion object {
        private const val TAG = "RecordAdShownUseCase"
    }
}
