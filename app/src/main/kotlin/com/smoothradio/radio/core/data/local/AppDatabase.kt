package com.smoothradio.radio.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.smoothradio.radio.core.domain.model.RadioStation

@Database(entities = [RadioStation::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun radioStationDao(): RadioStationDao
}
