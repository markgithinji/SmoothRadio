package com.smoothradio.radio.core.domain.repository

import com.smoothradio.radio.core.domain.model.AdSettings

interface AdSettingsRepository {
    suspend fun getAdSettings(): AdSettings
    suspend fun updateAdDataWithCount(currentTime: Long, currentHour: Long, newCount: Long)
    suspend fun updateAdSettings(intervalMinutes: Int, maxAdsPerHour: Int)
    suspend fun clearAll()
}
