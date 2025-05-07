package com.smoothradio.radio.core.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.*;

import com.smoothradio.radio.core.model.RadioStation;

import java.util.List;

@Dao
public interface RadioStationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertStations(List<RadioStation> stations);

    @Query("SELECT * FROM radio_stations")
    LiveData<List<RadioStation>> getAllStations();

    @Query("SELECT * FROM radio_stations WHERE isFavorite = 1")
    LiveData<List<RadioStation>> getFavoriteStations();

    @Query("SELECT * FROM radio_stations ORDER BY stationName COLLATE NOCASE ASC")
    LiveData<List<RadioStation>> getAllStationsSorted();

    @Query("UPDATE radio_stations SET isFavorite = :isFav WHERE id = :id")
    void updateFavoriteStatus(int id, boolean isFav);

    @Query("DELETE FROM radio_stations")
    void clearAll();
}
