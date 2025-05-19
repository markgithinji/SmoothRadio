package com.smoothradio.radio.core.data.repository

import com.smoothradio.radio.core.util.RadioStationLinksHelper
import com.smoothradio.radio.core.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeRadioLinkRepository : RadioLinkRepository {

    private val _linksFlow = MutableStateFlow(
        Resource.Success(RadioStationLinksHelper.RADIO_STATIONS.toList())
    )
    var clearCalled = false

    override fun getRemoteStreamLinksFlow(): Flow<Resource<List<String>>> = _linksFlow

    override fun clear() {
        clearCalled = true
    }
}
