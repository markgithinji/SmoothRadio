package com.smoothradio.radio.core.domain.repository

import com.smoothradio.radio.core.domain.model.RadioStation
import kotlinx.coroutines.flow.Flow

interface RadioRepository {
    val allStations: Flow<List<RadioStation>>
    val favoriteStations: Flow<List<RadioStation>>
    val playingStation: Flow<RadioStation?>

    suspend fun setPlayingStation(id: Int)
    suspend fun insertStations(stations: List<RadioStation>)
    suspend fun updateFavoriteStatus(id: Int, isFav: Boolean)
    suspend fun clearAllStations()
    suspend fun deleteStations(stations: List<RadioStation>)
}
