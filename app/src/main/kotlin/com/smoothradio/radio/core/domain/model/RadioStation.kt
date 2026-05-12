package com.smoothradio.radio.core.domain.model

import androidx.annotation.DrawableRes
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "radio_stations")
data class RadioStation(
    @PrimaryKey val id: Int,
    @param:DrawableRes val logoResource: Int,
    val stationName: String,
    val frequency: String,
    val location: String,
    val streamLink: String,
    val isPlaying: Boolean,
    val isFavorite: Boolean,
    val orderIndex: Int
) : ListItem
