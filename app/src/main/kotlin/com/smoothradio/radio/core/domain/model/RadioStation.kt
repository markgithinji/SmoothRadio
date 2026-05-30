package com.smoothradio.radio.core.domain.model

data class RadioStation(
    val id: Int,
    val stationName: String,
    val frequency: String,
    val location: String,
    val streamLink: String,
    val isPlaying: Boolean,
    val isFavorite: Boolean,
    val orderIndex: Int
) : ListItem
