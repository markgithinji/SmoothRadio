package com.smoothradio.radio.core.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smoothradio.radio.core.domain.model.RadioStation
import kotlinx.coroutines.flow.Flow

@Dao
interface RadioStationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStations(stations: List<RadioStation>)

    @Query("SELECT * FROM radio_stations ORDER BY orderIndex ASC")
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

    @Delete
    suspend fun deleteStations(stations: List<RadioStation>)
}

