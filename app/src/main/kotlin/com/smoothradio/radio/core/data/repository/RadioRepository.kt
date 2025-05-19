package com.smoothradio.radio.core.data.repository

import com.smoothradio.radio.core.model.RadioStation
import kotlinx.coroutines.flow.Flow

interface RadioRepository {
    val allStations: Flow<List<RadioStation>>
    val favoriteStations: Flow<List<RadioStation>>
    val playingStation: Flow<RadioStation?>

    suspend fun setPlayingStation(id: Int)
    suspend fun insertStations(stations: List<RadioStation>)
    suspend fun updateFavoriteStatus(id: Int, isFav: Boolean)
}
