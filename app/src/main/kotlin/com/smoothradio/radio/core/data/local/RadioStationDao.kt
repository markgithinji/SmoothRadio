package com.smoothradio.radio.core.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.smoothradio.radio.core.data.local.model.RadioStationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RadioStationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStations(stations: List<RadioStationEntity>)

    @Query("SELECT * FROM radio_stations ORDER BY orderIndex ASC")
    fun getAllStations(): Flow<List<RadioStationEntity>>

    @Query("SELECT * FROM radio_stations WHERE isFavorite = 1")
    fun getFavoriteStations(): Flow<List<RadioStationEntity>>

    @Query("UPDATE radio_stations SET isPlaying = 0")
    suspend fun clearPlayingState()

    @Query("UPDATE radio_stations SET isPlaying = 1 WHERE id = :id")
    suspend fun updatePlayingStation(id: Int)

    @Query("SELECT * FROM radio_stations WHERE isPlaying = 1 LIMIT 1")
    fun getPlayingStation(): Flow<RadioStationEntity?>

    @Transaction
    suspend fun setCurrentPlayingStation(id: Int) {
        clearPlayingState()
        updatePlayingStation(id)
    }

    @Query("UPDATE radio_stations SET isFavorite = :isFav WHERE id = :id")
    suspend fun updateFavoriteStatus(id: Int, isFav: Boolean)

    @Query("DELETE FROM radio_stations")
    suspend fun clearAll()

    @Delete
    suspend fun deleteStations(stations: List<RadioStationEntity>)
}
