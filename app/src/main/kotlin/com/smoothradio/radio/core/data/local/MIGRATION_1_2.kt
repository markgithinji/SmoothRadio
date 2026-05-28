package com.smoothradio.radio.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Create the new table
        db.execSQL(
            """
            CREATE TABLE radio_stations_new (
                id INTEGER PRIMARY KEY NOT NULL,
                stationName TEXT NOT NULL,
                frequency TEXT NOT NULL,
                location TEXT NOT NULL,
                streamLink TEXT NOT NULL,
                isPlaying INTEGER NOT NULL,
                isFavorite INTEGER NOT NULL,
                orderIndex INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // 2. Copy the data
        db.execSQL(
            """
            INSERT INTO radio_stations_new (id, stationName, frequency, location, streamLink, isPlaying, isFavorite, orderIndex)
            SELECT id, stationName, frequency, location, streamLink, isPlaying, isFavorite, orderIndex FROM radio_stations
            """.trimIndent()
        )

        // 3. Remove the old table
        db.execSQL("DROP TABLE radio_stations")

        // 4. Change the name of the new table to the correct name
        db.execSQL("ALTER TABLE radio_stations_new RENAME TO radio_stations")
    }
}
