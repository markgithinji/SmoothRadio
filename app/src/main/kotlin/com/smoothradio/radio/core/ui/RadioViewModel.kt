package com.smoothradio.radio.core.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.domain.repository.RadioLinkRepository
import com.smoothradio.radio.core.domain.repository.RadioRepository
import com.smoothradio.radio.core.domain.usecase.ProcessRemoteLinksUseCase
import com.smoothradio.radio.core.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RadioViewModel @Inject constructor(
    application: Application,
    private val radioLinkRepository: RadioLinkRepository,
    private val radioRepository: RadioRepository,
    private val processRemoteLinksUseCase: ProcessRemoteLinksUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase
) : AndroidViewModel(application) {

    val allStations = radioRepository.allStations
    val playingStation = radioRepository.playingStation
    val favoriteStations = radioRepository.favoriteStations

    private val _selectedStation = MutableSharedFlow<RadioStation?>(replay = 0)
    val selectedStation: SharedFlow<RadioStation?> = _selectedStation

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _favoriteToggleResult = MutableSharedFlow<Boolean>()
    val favoriteToggleResult: SharedFlow<Boolean> = _favoriteToggleResult

    fun observeAndProcessRemoteLinks() {
        viewModelScope.launch {
            processRemoteLinksUseCase()
        }
    }

    // Radio Station Logic
    fun savePlayingStationId(id: Int) {
        viewModelScope.launch {
            radioRepository.setPlayingStation(id)
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

    // Player Logic
    fun setSelectedStation(station: RadioStation?) {
        viewModelScope.launch {
            _selectedStation.emit(station)
        }
    }

    // ViewPager Current Page Logic
    fun setCurrentPage(page: Int) {
        _currentPage.value = page
    }

    public override fun onCleared() {
        super.onCleared()
        radioLinkRepository.clear()
    }
}
