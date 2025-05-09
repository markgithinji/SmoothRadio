package com.smoothradio.radio.core.data.local

import androidx.lifecycle.LiveData
import androidx.room.*
import com.smoothradio.radio.core.model.RadioStation
import kotlinx.coroutines.flow.Flow

@Dao
interface RadioStationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStations(stations: List<RadioStation>)

    @Query("SELECT * FROM radio_stations")
    fun getAllStations(): Flow<List<RadioStation>>

    @Query("SELECT * FROM radio_stations WHERE isFavorite = 1")
    fun getFavoriteStations(): Flow<List<RadioStation>>

    @Query("UPDATE radio_stations SET isPlaying = 0")
    suspend fun clearPlayingState()

    @Query("UPDATE radio_stations SET isPlaying = 1 WHERE id = :id")
    suspend fun updatePlayingStation(id: Int)

    @Query("SELECT * FROM radio_stations WHERE isPlaying = 1 LIMIT 1")
    fun getPlayingStation(): Flow<RadioStation?>

    @Query("UPDATE radio_stations SET isFavorite = :isFav WHERE id = :id")
    suspend fun updateFavoriteStatus(id: Int, isFav: Boolean)

    @Query("DELETE FROM radio_stations")
    suspend fun clearAll()
}

