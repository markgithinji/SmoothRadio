package com.smoothradio.radio.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.smoothradio.radio.core.domain.repository.ViewPreferenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DefaultViewPreferenceRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ViewPreferenceRepository {

    companion object {
        private val IS_GRID_VIEW_KEY = booleanPreferencesKey("is_grid_view")
    }

    override suspend fun saveIsGridView(isGridView: Boolean) {
        dataStore.edit { preferences ->
            preferences[IS_GRID_VIEW_KEY] = isGridView
        }
    }

    override suspend fun getIsGridView(): Boolean {
        return dataStore.data.map { preferences ->
            preferences[IS_GRID_VIEW_KEY] ?: false
        }.first()
    }

    override fun getIsGridViewFlow(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[IS_GRID_VIEW_KEY] ?: false
        }
    }
}