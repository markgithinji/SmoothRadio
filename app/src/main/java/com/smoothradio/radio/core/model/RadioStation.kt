package com.smoothradio.radio.core.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "radio_stations")
class RadioStation(
    @PrimaryKey val id: Int,
    val logoResource: Int,
    val stationName: String,
    val frequency: String,
    val location: String,
    val url: String,
    var isPlaying: Boolean,
    var isFavorite: Boolean
) : ListItem {

    constructor(other: RadioStation) : this(
        id = other.id,
        logoResource = other.logoResource,
        stationName = other.stationName,
        frequency = other.frequency,
        location = other.location,
        url = other.url,
        isPlaying = other.isPlaying,
        isFavorite = other.isFavorite
    )

    override fun equals(other: Any?): Boolean {
        return this === other || (other is RadioStation && id == other.id)
    }

    override fun hashCode(): Int = id.hashCode()
}
