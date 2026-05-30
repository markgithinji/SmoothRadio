package com.smoothradio.radio.core.data.mapper

import com.smoothradio.radio.core.data.local.model.RadioStationEntity
import com.smoothradio.radio.core.domain.model.RadioStation

fun RadioStationEntity.toDomain(): RadioStation {
    return RadioStation(
        id = id,
        stationName = stationName,
        frequency = frequency,
        location = location,
        streamLink = streamLink,
        isPlaying = isPlaying,
        isFavorite = isFavorite,
        orderIndex = orderIndex
    )
}

fun RadioStation.toEntity(): RadioStationEntity {
    return RadioStationEntity(
        id = id,
        stationName = stationName,
        frequency = frequency,
        location = location,
        streamLink = streamLink,
        isPlaying = isPlaying,
        isFavorite = isFavorite,
        orderIndex = orderIndex
    )
}
