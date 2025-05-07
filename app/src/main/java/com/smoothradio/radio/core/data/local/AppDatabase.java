package com.smoothradio.radio.core.data.local;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.smoothradio.radio.core.model.RadioStation;

@Database(entities = {RadioStation.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract RadioStationDao radioStationDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "radio_db")
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
