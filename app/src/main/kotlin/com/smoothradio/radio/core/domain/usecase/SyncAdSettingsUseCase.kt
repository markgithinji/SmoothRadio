package com.smoothradio.radio.core.domain.usecase

import com.smoothradio.radio.core.logging.LoggingHelper
import com.smoothradio.radio.core.domain.repository.AdSettingsRepository
import com.smoothradio.radio.core.domain.repository.RadioLinkRepository
import com.smoothradio.radio.core.util.Resource
import javax.inject.Inject

class SyncAdSettingsUseCase @Inject constructor(
    private val adSettingsRepository: AdSettingsRepository,
    private val radioLinkRepository: RadioLinkRepository
) {

    suspend operator fun invoke() {
        radioLinkRepository.getRemoteAdSettingsFlow().collect { resource ->
            when (resource) {
                is Resource.Success -> {
                    LoggingHelper.d(
                        message = "Received remote ad settings - Interval: ${resource.data.adShowIntervalMinutes} min, Max per hour: ${resource.data.maxAdsPerHour}",
                        tag = "SyncAdSettingsUseCase"
                    )

                    adSettingsRepository.updateAdSettings(
                        intervalMinutes = resource.data.adShowIntervalMinutes,
                        maxAdsPerHour = resource.data.maxAdsPerHour
                    )
                }

                is Resource.Error -> {
                    LoggingHelper.w(
                        message = "Failed to sync ad settings: ${resource.message}",
                        tag = "SyncAdSettingsUseCase"
                    )
                }

                else -> {}
            }
        }
    }
}