package com.smoothradio.radio.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.smoothradio.radio.core.domain.repository.AdSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DefaultAdSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : AdSettingsRepository {

    override suspend fun getLastAdShowTime(): Long {
        return dataStore.data.map { preferences ->
            preferences[LAST_AD_SHOW_TIME_KEY] ?: 0L
        }.first()
    }

    override suspend fun getAdShowCount(): Long {
        return dataStore.data.map { preferences ->
            preferences[AD_SHOW_COUNT_KEY] ?: 0L
        }.first()
    }

    override suspend fun getLastAdHour(): Long {
        return dataStore.data.map { preferences ->
            preferences[AD_SHOW_HOUR_KEY] ?: 0L
        }.first()
    }

    override suspend fun updateAdDataWithCount(
        currentTime: Long,
        currentHour: Long,
        newCount: Long
    ) {
        dataStore.edit { preferences ->
            preferences[LAST_AD_SHOW_TIME_KEY] = currentTime
            preferences[AD_SHOW_HOUR_KEY] = currentHour
            preferences[AD_SHOW_COUNT_KEY] = newCount
        }
    }

    override suspend fun updateAdSettings(intervalMinutes: Int, maxAdsPerHour: Int) {
        dataStore.edit { preferences ->
            preferences[AD_SHOW_INTERVAL_KEY] = intervalMinutes
            preferences[MAX_ADS_PER_HOUR_KEY] = maxAdsPerHour
        }
    }

    override suspend fun getAdShowIntervalMinutes(): Int {
        return dataStore.data.map { preferences ->
            preferences[AD_SHOW_INTERVAL_KEY] ?: DEFAULT_INTERVAL_MINUTES
        }.first()
    }

    override suspend fun getMaxAdsPerHour(): Int {
        return dataStore.data.map { preferences ->
            preferences[MAX_ADS_PER_HOUR_KEY] ?: DEFAULT_MAX_ADS_PER_HOUR
        }.first()
    }

    override suspend fun clearAll() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    companion object {
        private val LAST_AD_SHOW_TIME_KEY = longPreferencesKey("last_ad_show_time")
        private val AD_SHOW_COUNT_KEY = longPreferencesKey("ad_show_count")
        private val AD_SHOW_HOUR_KEY = longPreferencesKey("ad_show_hour")

        private val AD_SHOW_INTERVAL_KEY = intPreferencesKey("ad_show_interval_minutes")
        private val MAX_ADS_PER_HOUR_KEY = intPreferencesKey("max_ads_per_hour")

        private const val DEFAULT_INTERVAL_MINUTES = 4
        private const val DEFAULT_MAX_ADS_PER_HOUR = 4
    }
}