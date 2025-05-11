package com.smoothradio.radio.core.data.repository

import com.smoothradio.radio.core.data.local.RadioStationDao
import com.smoothradio.radio.core.model.RadioStation
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RadioRepository @Inject constructor(
    private val dao: RadioStationDao
) {
    // Read operations
    val allStations: Flow<List<RadioStation>> = dao.getAllStations()
    val favoriteStations: Flow<List<RadioStation>> = dao.getFavoriteStations()
    val playingStation: Flow<RadioStation?> = dao.getPlayingStation()

    // Write operations
    suspend fun setPlayingStation(id: Int) {
        dao.clearPlayingState()
        dao.updatePlayingStation(id)
    }

    suspend fun insertStations(stations: List<RadioStation>) = dao.insertStations(stations)

    suspend fun updateFavoriteStatus(id: Int, isFav: Boolean) = dao.updateFavoriteStatus(id, isFav)
}