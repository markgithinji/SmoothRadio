package com.smoothradio.radio.core.data.mapper

import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.data.local.model.RadioStationEntity
import com.smoothradio.radio.core.domain.model.RadioStation
import org.junit.Test

class RadioStationMapperTest {

    @Test
    fun entityToDomain_mapsCorrectlly() {
        val entity = RadioStationEntity(
            id = 1,
            stationName = "Test Station",
            frequency = "101.1",
            location = "Test City",
            streamLink = "http://test.com",
            isPlaying = true,
            isFavorite = true,
            orderIndex = 5
        )

        val domain = entity.toDomain()

        assertThat(domain.id).isEqualTo(entity.id)
        assertThat(domain.stationName).isEqualTo(entity.stationName)
        assertThat(domain.frequency).isEqualTo(entity.frequency)
        assertThat(domain.location).isEqualTo(entity.location)
        assertThat(domain.streamLink).isEqualTo(entity.streamLink)
        assertThat(domain.isPlaying).isEqualTo(entity.isPlaying)
        assertThat(domain.isFavorite).isEqualTo(entity.isFavorite)
        assertThat(domain.orderIndex).isEqualTo(entity.orderIndex)
    }

    @Test
    fun domainToEntity_mapsCorrectly() {
        val domain = RadioStation(
            id = 2,
            stationName = "Domain Station",
            frequency = "99.9",
            location = "Domain City",
            streamLink = "http://domain.com",
            isPlaying = false,
            isFavorite = false,
            orderIndex = 10
        )

        val entity = domain.toEntity()

        assertThat(entity.id).isEqualTo(domain.id)
        assertThat(entity.stationName).isEqualTo(domain.stationName)
        assertThat(entity.frequency).isEqualTo(domain.frequency)
        assertThat(entity.location).isEqualTo(domain.location)
        assertThat(entity.streamLink).isEqualTo(domain.streamLink)
        assertThat(entity.isPlaying).isEqualTo(domain.isPlaying)
        assertThat(entity.isFavorite).isEqualTo(domain.isFavorite)
        assertThat(entity.orderIndex).isEqualTo(domain.orderIndex)
    }
}
