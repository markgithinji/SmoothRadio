package com.smoothradio.radio.core.domain.repository

import com.smoothradio.radio.core.domain.model.RemoteAdSettings
import com.smoothradio.radio.core.util.Resource
import kotlinx.coroutines.flow.Flow

interface RadioLinkRepository {
    fun getRemoteStreamLinksFlow(): Flow<Resource<List<String>>>
    fun getRemoteAdSettingsFlow(): Flow<Resource<RemoteAdSettings>>
    fun clear()
}