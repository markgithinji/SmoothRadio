package com.smoothradio.radio.core.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.domain.repository.RadioLinkRepository
import com.smoothradio.radio.core.domain.repository.RadioRepository
import com.smoothradio.radio.core.domain.repository.ViewPreferenceRepository
import com.smoothradio.radio.core.domain.usecase.ProcessRemoteLinksUseCase
import com.smoothradio.radio.core.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RadioViewModel @Inject constructor(
    application: Application,
    private val radioLinkRepository: RadioLinkRepository,
    private val radioRepository: RadioRepository,
    private val processRemoteLinksUseCase: ProcessRemoteLinksUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val viewPreferenceRepository: ViewPreferenceRepository
) : AndroidViewModel(application) {

    val allStations = radioRepository.allStations
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val favoriteStations = radioRepository.favoriteStations
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _favoriteToggleResult = MutableSharedFlow<Boolean>()
    val favoriteToggleResult: SharedFlow<Boolean> = _favoriteToggleResult

    private val _isGridView = MutableStateFlow(false)
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    init {
        loadViewPreference()
    }

    private fun loadViewPreference() {
        viewModelScope.launch {
            viewPreferenceRepository.getIsGridViewFlow().collect { isGridView ->
                _isGridView.value = isGridView
            }
        }
    }

    fun toggleViewPreference() {
        viewModelScope.launch {
            val newValue = !_isGridView.value
            viewPreferenceRepository.saveIsGridView(newValue)
        }
    }

    fun observeAndProcessRemoteLinks() {
        viewModelScope.launch {
            processRemoteLinksUseCase()
        }
    }

    fun insertStations(stations: List<RadioStation>) {
        viewModelScope.launch {
            radioRepository.insertStations(stations)
        }
    }

    fun toggleFavorite(stationId: Int, newState: Boolean) {
        viewModelScope.launch {
            val result = toggleFavoriteUseCase(stationId, newState)
            _favoriteToggleResult.emit(result)
        }
    }

    fun setCurrentPage(page: Int) {
        _currentPage.value = page
    }

    override fun onCleared() {
        super.onCleared()
        radioLinkRepository.clear()
    }
}
