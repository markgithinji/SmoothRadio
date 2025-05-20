package com.smoothradio.radio.core.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "radio_stations")
data class RadioStation(
    @PrimaryKey val id: Int,
    val logoResource: Int,
    val stationName: String,
    val frequency: String,
    val location: String,
    val streamLink: String,
    var isPlaying: Boolean,
    var isFavorite: Boolean
) : ListItem {

    override fun equals(other: Any?): Boolean {
        return this === other || (other is RadioStation && id == other.id)
    }

    override fun hashCode(): Int = id.hashCode()
}
