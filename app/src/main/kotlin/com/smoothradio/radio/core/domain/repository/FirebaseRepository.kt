package com.smoothradio.radio.core.domain.repository

import com.smoothradio.radio.core.domain.model.RemoteAdSettings
import com.smoothradio.radio.core.util.Resource
import kotlinx.coroutines.flow.Flow

interface FirebaseRepository {
    fun getRemoteStreamLinksFlow(): Flow<Resource<List<String>>>
    fun getRemoteAdSettingsFlow(): Flow<Resource<RemoteAdSettings>>
    suspend fun submitReport(
        category: String,
        description: String,
        appVersion: String,
        deviceInfo: String,
        androidVersion: String
    ): Resource<Unit>
    fun clear()
}
