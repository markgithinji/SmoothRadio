package com.smoothradio.radio.core.data.repository

import com.smoothradio.radio.core.data.local.RadioStationDao
import com.smoothradio.radio.core.data.mapper.toDomain
import com.smoothradio.radio.core.data.mapper.toEntity
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.domain.repository.RadioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultRadioRepository @Inject constructor(
    private val dao: RadioStationDao
) : RadioRepository {

    override val allStations: Flow<List<RadioStation>> = 
        dao.getAllStations().map { entities -> entities.map { it.toDomain() } }
        
    override val favoriteStations: Flow<List<RadioStation>> = 
        dao.getFavoriteStations().map { entities -> entities.map { it.toDomain() } }
        
    override val playingStation: Flow<RadioStation?> = 
        dao.getPlayingStation().map { it?.toDomain() }

    override suspend fun setPlayingStation(id: Int) {
        dao.setCurrentPlayingStation(id)
    }

    override suspend fun insertStations(stations: List<RadioStation>) {
        dao.insertStations(stations.map { it.toEntity() })
    }

    override suspend fun updateFavoriteStatus(id: Int, isFav: Boolean) =
        dao.updateFavoriteStatus(id, isFav)

    override suspend fun clearAllStations() = dao.clearAll()

    override suspend fun deleteStations(stations: List<RadioStation>) {
        dao.deleteStations(stations.map { it.toEntity() })
    }
}
