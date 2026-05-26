package com.smoothradio.radio.core.data.repository

import com.smoothradio.radio.core.domain.model.RemoteAdSettings
import com.smoothradio.radio.core.domain.repository.FirebaseRepository
import com.smoothradio.radio.core.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeFirebaseRepository(
    initialLinks: List<String>? = null
) : FirebaseRepository {

    private val safeLinks = initialLinks ?: List(232) { i -> "https://stream$i.com" } // 232 links required
    var clearCalled: Boolean = false

    override fun getRemoteStreamLinksFlow(): Flow<Resource<List<String>>> = flow {
        emit(Resource.Success(safeLinks))
    }

    override fun getRemoteAdSettingsFlow(): Flow<Resource<RemoteAdSettings>> = flow {
        emit(Resource.Success(RemoteAdSettings(4, 4)))
    }

    override fun clear() {
        clearCalled = true
    }
}
