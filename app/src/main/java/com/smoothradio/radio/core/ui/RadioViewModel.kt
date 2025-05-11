package com.smoothradio.radio.core.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smoothradio.radio.core.data.repository.RadioLinkRepository
import com.smoothradio.radio.core.data.repository.RadioRepository
import com.smoothradio.radio.core.model.RadioStation
import com.smoothradio.radio.core.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RadioViewModel @Inject constructor(
    application: Application,
    private val radioLinkRepository: RadioLinkRepository,
    private val radioRepository: RadioRepository
) : AndroidViewModel(application) {

    val allStations = radioRepository.allStations

    val playingStation = radioRepository.playingStation


    val remoteLinks: Flow<Resource<List<String>>> = radioLinkRepository
        .getRemoteStreamLinksFlow()
        .shareIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
        )

    val favoriteStations = radioRepository.favoriteStations

    private val _selectedStation = MutableSharedFlow<RadioStation?>(replay = 0)
    val selectedStation: SharedFlow<RadioStation?> = _selectedStation

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

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

    fun updateFavoriteStatus(id: Int, isFav: Boolean) {
        viewModelScope.launch {
            radioRepository.updateFavoriteStatus(id, isFav)
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

    override fun onCleared() {
        super.onCleared()
        radioLinkRepository.clear()
    }
}