package com.smoothradio.radio.core.domain.usecase

import com.smoothradio.radio.LoggingHelper
import com.smoothradio.radio.core.domain.repository.AdSettingsRepository
import javax.inject.Inject

class RecordAdShownUseCase @Inject constructor(
    private val adSettingsRepository: AdSettingsRepository
) {

    companion object {
        private const val TAG = "RecordAdShownUseCase"
    }

    suspend operator fun invoke() {
        val currentTime = System.currentTimeMillis()
        val currentHour = getCurrentHour()
        val lastHour = adSettingsRepository.getLastAdHour()
        val currentCount = adSettingsRepository.getAdShowCount()

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
}