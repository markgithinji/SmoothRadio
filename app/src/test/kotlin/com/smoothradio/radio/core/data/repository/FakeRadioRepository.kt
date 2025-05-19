package com.smoothradio.radio.core.data.repository

import com.smoothradio.radio.core.model.RadioStation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeRadioRepository : RadioRepository {

    private val _allStations = MutableStateFlow<List<RadioStation>>(emptyList())
    private val _favoriteStations = MutableStateFlow<List<RadioStation>>(emptyList())
    private val _playingStation = MutableStateFlow<RadioStation?>(null)

    override val allStations: Flow<List<RadioStation>> = _allStations
    override val favoriteStations: Flow<List<RadioStation>> = _favoriteStations
    override val playingStation: Flow<RadioStation?> = _playingStation

    override suspend fun setPlayingStation(id: Int) {
        _playingStation.value = _allStations.value.find { it.id == id }
    }

    override suspend fun insertStations(stations: List<RadioStation>) {
        _allStations.value = stations
        _favoriteStations.value = stations.filter { it.isFavorite }
    }

    override suspend fun updateFavoriteStatus(id: Int, isFav: Boolean) {
        val updated = _allStations.value.map {
            if (it.id == id) it.copy(isFavorite = isFav) else it
        }
        _allStations.value = updated
        _favoriteStations.value = updated.filter { it.isFavorite }
    }
}
