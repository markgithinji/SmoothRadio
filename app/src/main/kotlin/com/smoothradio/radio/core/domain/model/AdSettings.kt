package com.smoothradio.radio.core.domain.model

data class AdSettings(
    val lastAdShowTime: Long,
    val adShowCount: Long,
    val lastAdHour: Long,
    val adShowIntervalMinutes: Int,
    val maxAdsPerHour: Int
)
