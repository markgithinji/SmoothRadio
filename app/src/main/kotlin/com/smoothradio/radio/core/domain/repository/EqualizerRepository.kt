package com.smoothradio.radio.core.domain.repository

import kotlinx.coroutines.flow.Flow

interface EqualizerRepository {
    suspend fun saveBandLevel(band: Int, level: Short)
    suspend fun getBandLevel(band: Int): Short
    fun getBandLevelsFlow(): Flow<Map<Int, Short>>
}
