package com.smoothradio.radio.core.data.repository

import com.smoothradio.radio.core.domain.model.RemoteAdSettings
import com.smoothradio.radio.core.domain.repository.FirebaseRepository
import com.smoothradio.radio.core.util.RadioStationLinksHelper
import com.smoothradio.radio.core.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeFirebaseRepository : FirebaseRepository {

    private val _linksFlow = MutableStateFlow(
        Resource.Success(
            RadioStationLinksHelper.RADIO_STATIONS.toList()
        )
    )

    private val _adSettingsFlow = MutableStateFlow<Resource<RemoteAdSettings>>(
        Resource.Success(RemoteAdSettings(4, 4))
    )

    var clearCalled = false // For verification in tests

    override fun getRemoteStreamLinksFlow(): Flow<Resource<List<String>>> = _linksFlow

    override fun getRemoteAdSettingsFlow(): Flow<Resource<RemoteAdSettings>> = _adSettingsFlow

    var lastReport: Map<String, String>? = null
    var reportResult: Resource<Unit> = Resource.Success(Unit)

    override suspend fun submitReport(
        category: String,
        description: String,
        appVersion: String,
        deviceInfo: String,
        androidVersion: String
    ): Resource<Unit> {
        lastReport = mapOf(
            "category" to category,
            "description" to description,
            "appVersion" to appVersion,
            "deviceInfo" to deviceInfo,
            "androidVersion" to androidVersion
        )
        return reportResult
    }

    override fun clear() {
        clearCalled = true
    }
}
