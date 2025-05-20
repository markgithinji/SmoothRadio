package com.smoothradio.radio.core.data.local

import com.smoothradio.radio.core.domain.model.RadioStation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeRadioStationDao : RadioStationDao {

    private val stations = mutableListOf<RadioStation>()
    private var playingId: Int? = null

    override fun getAllStations(): Flow<List<RadioStation>> = flow {
        emit(stations.toList())
    }

    override fun getFavoriteStations(): Flow<List<RadioStation>> = flow {
        emit(stations.filter { it.isFavorite })
    }

    override fun getPlayingStation(): Flow<RadioStation?> = flow {
        emit(stations.find { it.isPlaying })
    }

    override suspend fun clearPlayingState() {
        playingId = null
        stations.replaceAll { it.copy(isPlaying = false) }
    }

    override suspend fun updatePlayingStation(id: Int) {
        playingId = id
        stations.replaceAll { it.copy(isPlaying = it.id == id) }
    }

    override suspend fun insertStations(stations: List<RadioStation>) {
        this.stations.clear()
        this.stations.addAll(stations)
    }

    override suspend fun updateFavoriteStatus(id: Int, isFav: Boolean) {
        val index = stations.indexOfFirst { it.id == id }
        if (index != -1) {
            stations[index] = stations[index].copy(isFavorite = isFav)
        }
    }

    override suspend fun clearAll() {
        stations.clear()
        playingId = null
    }
}
