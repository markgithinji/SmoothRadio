package com.smoothradio.radio.core.data.repository

import com.smoothradio.radio.core.domain.model.RemoteAdSettings
import com.smoothradio.radio.core.domain.repository.RadioLinkRepository
import com.smoothradio.radio.core.util.RadioStationLinksHelper
import com.smoothradio.radio.core.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeRadioLinkRepositoryAndroidTest : RadioLinkRepository {

    private val _linksFlow = MutableStateFlow(
        Resource.Success(
            RadioStationLinksHelper.RADIO_STATIONS.toList() // Emits static link list
        )
    )

    private val _adSettingsFlow = MutableStateFlow<Resource<RemoteAdSettings>>(
        Resource.Success(RemoteAdSettings(4, 4))
    )

    var clearCalled = false // For verification in tests

    override fun getRemoteStreamLinksFlow(): Flow<Resource<List<String>>> = _linksFlow

    override fun getRemoteAdSettingsFlow(): Flow<Resource<RemoteAdSettings>> = _adSettingsFlow

    fun emitLinks(links: List<String>) {
        _linksFlow.value = Resource.Success(links)
    }

    fun emitAdSettings(settings: RemoteAdSettings) {
        _adSettingsFlow.value = Resource.Success(settings)
    }

    override fun clear() {
        clearCalled = true
    }
}
