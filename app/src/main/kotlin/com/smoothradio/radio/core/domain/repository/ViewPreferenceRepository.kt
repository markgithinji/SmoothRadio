package com.smoothradio.radio.core.domain.repository

import kotlinx.coroutines.flow.Flow

interface ViewPreferenceRepository {
    suspend fun saveIsGridView(isGridView: Boolean)
    suspend fun getIsGridView(): Boolean
    fun getIsGridViewFlow(): Flow<Boolean>
}