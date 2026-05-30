package com.smoothradio.radio.core.data.mapper

import com.smoothradio.radio.core.data.model.AdSettingsDto
import com.smoothradio.radio.core.domain.model.RemoteAdSettings

fun AdSettingsDto.toDomain(defaultInterval: Int, defaultMaxAds: Int): RemoteAdSettings {
    return RemoteAdSettings(
        adShowIntervalMinutes = adShowIntervalMinutes?.toInt() ?: defaultInterval,
        maxAdsPerHour = maxAdsPerHour?.toInt() ?: defaultMaxAds
    )
}
