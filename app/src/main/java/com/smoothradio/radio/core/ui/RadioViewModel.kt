package com.smoothradio.radio.core.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smoothradio.radio.core.data.repository.RadioLinkRepository
import com.smoothradio.radio.core.data.repository.RadioRepository
import com.smoothradio.radio.core.model.RadioStation
import com.smoothradio.radio.core.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val repository: RadioRepository
) : AndroidViewModel(application) {

    // Radio Stations
    // In RadioViewModel.kt
    val allStations = repository.allStations
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )
    val favoriteStations = repository.favoriteStations
    // In RadioViewModel.kt
    val playingStation = repository.playingStation
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = null
        )

    // Selected Station
    private val _selectedStation = MutableStateFlow<RadioStation?>(null)
    val selectedStation: StateFlow<RadioStation?> = _selectedStation.asStateFlow()

    // Current Page
    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    // Remote Links
    private val _remoteLinks = MutableStateFlow<Resource<List<String>>>(Resource.loading())
    val remoteLinks: StateFlow<Resource<List<String>>> = _remoteLinks.asStateFlow()

    init {
        fetchRemoteLinks()
    }

    // -------------------
    // Radio Station Logic
    // -------------------
    fun savePlayingStationId(id: Int) {
        viewModelScope.launch {
            repository.setPlayingStation(id)
        }
    }

    fun insertStations(stations: List<RadioStation>) {
        viewModelScope.launch {
            repository.insertStations(stations)
        }
    }

    fun updateFavoriteStatus(id: Int, isFav: Boolean) {
        viewModelScope.launch {
            repository.updateFavoriteStatus(id, isFav)
        }
    }

    // -----------------------
    // Radio Links Logic
    // -----------------------
    private fun fetchRemoteLinks() {
        viewModelScope.launch {
            radioLinkRepository.remoteStreamLinks.collect { resource ->
                _remoteLinks.value = resource
            }
        }
    }

    // -----------------------
    // Player Logic
    // -----------------------
    fun setSelectedStation(station: RadioStation) {
        _selectedStation.value = station
    }

    //-------------------------
    // ViewPager Current Page Logic
    //-------------------------
    fun setCurrentPage(page: Int) {
        _currentPage.value = page
    }

    override fun onCleared() {
        super.onCleared()
        radioLinkRepository.clear()
    }
}