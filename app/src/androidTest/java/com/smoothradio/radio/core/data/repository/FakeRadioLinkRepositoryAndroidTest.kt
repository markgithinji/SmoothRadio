package com.smoothradio.radio.core.data.repository

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

    var clearCalled = false // For verification in tests

    override fun getRemoteStreamLinksFlow(): Flow<Resource<List<String>>> = _linksFlow

    override fun clear() {
        clearCalled = true
    }
}
