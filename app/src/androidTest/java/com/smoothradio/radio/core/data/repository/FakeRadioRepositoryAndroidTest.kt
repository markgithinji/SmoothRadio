package com.smoothradio.radio.core.data.repository

import com.smoothradio.radio.R
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.domain.repository.RadioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeRadioRepositoryAndroidTest : RadioRepository {

    private val initialStations = listOf(
        RadioStation(
            id = 1,
            logoResource = R.drawable.hopefm,
            stationName = "Hope FM",
            frequency = "101.1",
            location = "Nairobi",
            streamLink = "https://example.com/hopefm",
            isPlaying = false,
            isFavorite = false,
            orderIndex = 0
        ),
        RadioStation(
            id = 2,
            logoResource = R.drawable.soundcityradiologo,
            stationName = "Sound City",
            frequency = "102.2",
            location = "Mombasa",
            streamLink = "https://example.com/soundcity",
            isPlaying = false,
            isFavorite = true,
            orderIndex = 1
        ),
        RadioStation( // KIKUYU
            id = 38,
            logoResource = R.drawable.inooro,
            stationName = "Inooro FM",
            frequency = "104.4",
            location = "Nyeri",
            streamLink = "https://example.com/inooro",
            isPlaying = false,
            isFavorite = false,
            orderIndex = 2
        )
    )

    private val _allStations = MutableStateFlow(initialStations)
    private val _favoriteStations = MutableStateFlow(initialStations.filter { it.isFavorite })
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

    override suspend fun clearAllStations() {
        _allStations.value = emptyList()
        _favoriteStations.value = emptyList()
        _playingStation.value = null
    }
}
