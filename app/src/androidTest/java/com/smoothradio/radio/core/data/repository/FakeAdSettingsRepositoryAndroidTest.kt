package com.smoothradio.radio.core.data.repository

import com.smoothradio.radio.core.domain.repository.AdSettingsRepository

class FakeAdSettingsRepositoryAndroidTest : AdSettingsRepository {

    private var lastAdShowTime: Long = 0L
    private var adShowCount: Long = 0L
    private var lastAdHour: Long = 0L
    private var adShowIntervalMinutes: Int = 4
    private var maxAdsPerHour: Int = 4

    override suspend fun getLastAdShowTime(): Long = lastAdShowTime

    override suspend fun getAdShowCount(): Long = adShowCount

    override suspend fun getLastAdHour(): Long = lastAdHour

    override suspend fun updateAdDataWithCount(currentTime: Long, currentHour: Long, newCount: Long) {
        lastAdShowTime = currentTime
        lastAdHour = currentHour
        adShowCount = newCount
    }

    override suspend fun getAdShowIntervalMinutes(): Int = adShowIntervalMinutes

    override suspend fun getMaxAdsPerHour(): Int = maxAdsPerHour

    override suspend fun updateAdSettings(intervalMinutes: Int, maxAdsPerHour: Int) {
        this.adShowIntervalMinutes = intervalMinutes
        this.maxAdsPerHour = maxAdsPerHour
    }

    override suspend fun clearAll() {
        lastAdShowTime = 0L
        adShowCount = 0L
        lastAdHour = 0L
        adShowIntervalMinutes = 4
        maxAdsPerHour = 4
    }
}
