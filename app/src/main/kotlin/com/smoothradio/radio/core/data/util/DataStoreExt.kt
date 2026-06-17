package com.smoothradio.radio.core.data.util

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Safely reads a value from DataStore, handling IOExceptions by emitting empty preferences.
 */
fun <T> DataStore<Preferences>.safeGet(
    key: Preferences.Key<T>,
    defaultValue: T
): Flow<T> = safeData()
    .map { preferences ->
        preferences[key] ?: defaultValue
    }

/**
 * Safely exposes the DataStore data flow, handling IOExceptions.
 */
fun DataStore<Preferences>.safeData(): Flow<Preferences> = data
    .catch { exception ->
        if (exception is IOException) emit(emptyPreferences())
        else throw exception
    }
