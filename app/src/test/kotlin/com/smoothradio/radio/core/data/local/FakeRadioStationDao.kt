package com.smoothradio.radio.core.data.local

import com.smoothradio.radio.core.domain.model.RadioStation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeRadioStationDao : RadioStationDao {

    private val stations = mutableListOf<RadioStation>()
    private val _stationsFlow = MutableStateFlow<List<RadioStation>>(emptyList())

    private fun refreshFlows() {
        _stationsFlow.value = stations.toList()
    }

    override fun getAllStations(): Flow<List<RadioStation>> = _stationsFlow

    override fun getFavoriteStations(): Flow<List<RadioStation>> = _stationsFlow.map { list ->
        list.filter { it.isFavorite }
    }

    override fun getPlayingStation(): Flow<RadioStation?> = _stationsFlow.map { list ->
        list.find { it.isPlaying }
    }

    override suspend fun clearPlayingState() {
        stations.replaceAll { it.copy(isPlaying = false) }
        refreshFlows()
    }

    override suspend fun updatePlayingStation(id: Int) {
        stations.replaceAll { it.copy(isPlaying = it.id == id) }
        refreshFlows()
    }

    override suspend fun insertStations(stations: List<RadioStation>) {
        stations.forEach { newStation ->
            val index = this.stations.indexOfFirst { it.id == newStation.id }
            if (index != -1) {
                this.stations[index] = newStation
            } else {
                this.stations.add(newStation)
            }
        }
        refreshFlows()
    }

    override suspend fun updateFavoriteStatus(id: Int, isFav: Boolean) {
        val index = stations.indexOfFirst { it.id == id }
        if (index != -1) {
            stations[index] = stations[index].copy(isFavorite = isFav)
            refreshFlows()
        }
    }

    override suspend fun clearAll() {
        stations.clear()
        refreshFlows()
    }

    override suspend fun deleteStations(stations: List<RadioStation>) {
        val idsToDelete = stations.map { it.id }.toSet()
        this.stations.removeAll { it.id in idsToDelete }
        refreshFlows()
    }
}
