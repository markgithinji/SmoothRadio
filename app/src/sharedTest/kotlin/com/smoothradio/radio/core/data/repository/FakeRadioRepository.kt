package com.smoothradio.radio.core.data.repository

import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.domain.repository.RadioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeRadioRepository : RadioRepository {

    private val initialStations = listOf(
        RadioStation(
            id = 0,
            stationName = "HOPE FM",
            frequency = "93.3",
            location = "NAIROBI",
            streamLink = "https://example.com/hopefm",
            isPlaying = false,
            isFavorite = false,
            orderIndex = 0
        ),
        RadioStation(
            id = 1,
            stationName = "SOUNDCITY RADIO",
            frequency = "88.5",
            location = "NAIROBI",
            streamLink = "https://example.com/soundcity",
            isPlaying = false,
            isFavorite = true,
            orderIndex = 1
        ),
        RadioStation(
            id = 228,
            stationName = "RADIO 47",
            frequency = "103.0",
            location = "NAIROBI",
            streamLink = "https://example.com/radio47",
            isPlaying = false,
            isFavorite = false,
            orderIndex = 2
        ),
        RadioStation(
            id = 4,
            stationName = "INOORO FM",
            frequency = "98.9",
            location = "NAIROBI",
            streamLink = "https://example.com/inooro",
            isPlaying = false,
            isFavorite = false,
            orderIndex = 3
        ),
        RadioStation(
            id = 11,
            stationName = "KAMEME FM",
            frequency = "101.1",
            location = "NAIROBI",
            streamLink = "https://example.com/kameme",
            isPlaying = false,
            isFavorite = false,
            orderIndex = 4
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
