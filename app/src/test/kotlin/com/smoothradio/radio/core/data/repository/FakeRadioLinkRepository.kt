package com.smoothradio.radio.core.data.repository

import com.smoothradio.radio.core.domain.repository.RadioLinkRepository
import com.smoothradio.radio.core.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeRadioLinkRepository : RadioLinkRepository {

    private val safeLinks = List(232) { i -> "https://stream$i.com" } // 232 links required
    var clearCalled: Boolean = false

    override fun getRemoteStreamLinksFlow(): Flow<Resource<List<String>>> = flow {
        emit(Resource.Success(safeLinks))
    }

    override fun clear() {
        clearCalled = true
    }
}
