package com.smoothradio.radio.core.data.repository

import com.smoothradio.radio.core.domain.repository.EqualizerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeEqualizerRepositoryAndroidTest : EqualizerRepository {

    private val _bandLevels = MutableStateFlow<Map<Int, Short>>(emptyMap())

    override suspend fun saveBandLevel(band: Int, level: Short) {
        val current = _bandLevels.value.toMutableMap()
        current[band] = level
        _bandLevels.value = current
    }

    override suspend fun getBandLevel(band: Int): Short {
        return _bandLevels.value[band] ?: 0
    }

    override fun getBandLevelsFlow(): Flow<Map<Int, Short>> = _bandLevels.asStateFlow()
}
