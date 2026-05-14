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
            stationName = "HOPE FM",
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
            stationName = "SOUNDCITY RADIO",
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
            stationName = "INOORO FM",
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

    private fun updateFlows(updatedList: List<RadioStation>) {
        val sortedList = updatedList.sortedBy { it.orderIndex }
        _allStations.value = sortedList
        _favoriteStations.value = sortedList.filter { it.isFavorite }
        _playingStation.value = sortedList.find { it.isPlaying }
    }

    override suspend fun setPlayingStation(id: Int) {
        val updated = _allStations.value.map {
            it.copy(isPlaying = it.id == id)
        }
        updateFlows(updated)
    }

    override suspend fun insertStations(stations: List<RadioStation>) {
        val current = _allStations.value.toMutableList()
        stations.forEach { newStation ->
            val index = current.indexOfFirst { it.id == newStation.id }
            if (index != -1) {
                current[index] = newStation
            } else {
                current.add(newStation)
            }
        }
        updateFlows(current)
    }

    override suspend fun updateFavoriteStatus(id: Int, isFav: Boolean) {
        val updated = _allStations.value.map {
            if (it.id == id) it.copy(isFavorite = isFav) else it
        }
        updateFlows(updated)
    }

    override suspend fun deleteStations(stations: List<RadioStation>) {
        val updated = _allStations.value.filterNot { station ->
            stations.any { it.id == station.id }
        }
        updateFlows(updated)
    }

    override suspend fun clearAllStations() {
        updateFlows(emptyList())
    }
}
