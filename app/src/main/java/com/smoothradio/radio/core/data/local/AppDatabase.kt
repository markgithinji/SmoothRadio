package com.smoothradio.radio.core.data.local;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.smoothradio.radio.core.model.RadioStation;

@Database(entities = {RadioStation.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract RadioStationDao radioStationDao();
}
