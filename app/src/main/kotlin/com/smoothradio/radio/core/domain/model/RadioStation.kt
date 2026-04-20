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
    var isPlaying: Boolean,
    var isFavorite: Boolean,
    val orderIndex: Int
) : ListItem {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RadioStation) return false
        return id == other.id &&
                isFavorite == other.isFavorite &&
                isPlaying == other.isPlaying
    }

    override fun hashCode(): Int = id.hashCode()
}