package com.smoothradio.radio.core.domain.repository

import com.smoothradio.radio.core.domain.model.AdSettings
import kotlinx.coroutines.flow.Flow

interface AdSettingsRepository {
    val adSettings: Flow<AdSettings>
    suspend fun updateAdDataWithCount(currentTime: Long, currentHour: Long, newCount: Long)
    suspend fun updateAdSettings(intervalMinutes: Int, maxAdsPerHour: Int)
    suspend fun clearAll()
}
