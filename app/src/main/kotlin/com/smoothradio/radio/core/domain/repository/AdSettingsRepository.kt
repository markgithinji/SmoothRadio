package com.smoothradio.radio.core.domain.repository

interface AdSettingsRepository {
    // Ad frequency data (when ads were shown)
    suspend fun getLastAdShowTime(): Long
    suspend fun getAdShowCount(): Long
    suspend fun getLastAdHour(): Long
    suspend fun updateAdDataWithCount(currentTime: Long, currentHour: Long, newCount: Long)

    // Remote ad settings (cached from Firestore)
    suspend fun getAdShowIntervalMinutes(): Int
    suspend fun getMaxAdsPerHour(): Int
    suspend fun updateAdSettings(intervalMinutes: Int, maxAdsPerHour: Int)

    // Utility
    suspend fun clearAll()
}