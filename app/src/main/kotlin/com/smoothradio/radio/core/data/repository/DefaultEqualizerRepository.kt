package com.smoothradio.radio.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.smoothradio.radio.core.domain.repository.EqualizerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DefaultEqualizerRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : EqualizerRepository {

    override suspend fun saveBandLevel(band: Int, level: Short) {
        dataStore.edit { preferences ->
            preferences[intPreferencesKey("eq_band_$band")] = level.toInt()
        }
    }

    override suspend fun getBandLevel(band: Int): Short {
        return dataStore.data.map { preferences ->
            (preferences[intPreferencesKey("eq_band_$band")] ?: 0).toShort()
        }.first()
    }

    override fun getBandLevelsFlow(): Flow<Map<Int, Short>> {
        return dataStore.data.map { preferences ->
            val bands = mutableMapOf<Int, Short>()
            for (i in 0 until 5) { // 5 bands
                bands[i] = (preferences[intPreferencesKey("eq_band_$i")] ?: 0).toShort()
            }
            bands
        }
    }
}
