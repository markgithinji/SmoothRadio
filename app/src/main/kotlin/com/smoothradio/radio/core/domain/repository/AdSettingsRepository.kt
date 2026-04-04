package com.smoothradio.radio.core.domain.repository

interface AdSettingsRepository {
    suspend fun getLastAdShowTime(): Long
    suspend fun getAdShowCount(): Long
    suspend fun getLastAdHour(): Long
    suspend fun updateAdDataWithCount(currentTime: Long, currentHour: Long, newCount: Long)
    suspend fun clearAll()
}