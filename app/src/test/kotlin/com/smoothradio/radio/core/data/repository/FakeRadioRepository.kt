package com.smoothradio.radio.core.data.repository

import com.smoothradio.radio.core.data.local.FakeRadioStationDao
import com.smoothradio.radio.core.data.local.RadioStationDao
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.domain.repository.RadioRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Singleton

@Singleton
class FakeRadioRepository (
    private val dao: RadioStationDao
) : RadioRepository {

    override val allStations: Flow<List<RadioStation>> = dao.getAllStations()
    override val favoriteStations: Flow<List<RadioStation>> = dao.getFavoriteStations()
    override val playingStation: Flow<RadioStation?> = dao.getPlayingStation()

    override suspend fun setPlayingStation(id: Int) {
        dao.clearPlayingState()
        dao.updatePlayingStation(id)
    }

    override suspend fun insertStations(stations: List<RadioStation>) =
        dao.insertStations(stations)

    override suspend fun updateFavoriteStatus(id: Int, isFav: Boolean) =
        dao.updateFavoriteStatus(id, isFav)
}
