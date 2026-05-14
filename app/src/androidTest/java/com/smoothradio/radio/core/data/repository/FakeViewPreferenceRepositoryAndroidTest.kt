package com.smoothradio.radio.core.data.repository

import com.smoothradio.radio.core.domain.repository.ViewPreferenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeViewPreferenceRepositoryAndroidTest : ViewPreferenceRepository {
    private val _isGridView = MutableStateFlow(false)

    override suspend fun saveIsGridView(isGridView: Boolean) {
        _isGridView.value = isGridView
    }

    override suspend fun getIsGridView(): Boolean {
        return _isGridView.value
    }

    override fun getIsGridViewFlow(): Flow<Boolean> {
        return _isGridView
    }
}
