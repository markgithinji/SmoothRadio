package com.smoothradio.radio.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.smoothradio.radio.core.data.local.model.RadioStationEntity

@Database(entities = [RadioStationEntity::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun radioStationDao(): RadioStationDao
}
