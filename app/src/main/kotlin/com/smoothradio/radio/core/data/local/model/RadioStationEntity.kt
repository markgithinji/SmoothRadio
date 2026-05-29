package com.smoothradio.radio.core.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "radio_stations")
data class RadioStationEntity(
    @PrimaryKey val id: Int,
    val stationName: String,
    val frequency: String,
    val location: String,
    val streamLink: String,
    val isPlaying: Boolean,
    val isFavorite: Boolean,
    val orderIndex: Int
)
