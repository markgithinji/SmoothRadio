package com.smoothradio.radio.feature.radio_list.util

import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.model.RadioStation
import org.junit.Test

class RadioStationsHelperTest {

    @Test
    fun createRadioStations_generatesStationsFromLinks() {
        // Given: a list of 250 dummy links (all "http://stream")
        val links = List(250) { "http://stream/$it" }

        // When
        val stations = RadioStationsHelper.createRadioStations(links, null)

        // Then
        assertThat(stations).isNotEmpty()
        assertThat(stations[0].stationName).isEqualTo("HOPE FM")
        assertThat(stations[0].streamLink).isEqualTo("http://stream/0")
        assertThat(stations.find { it.id == 228 }?.stationName).isEqualTo("RADIO 47")
    }

    @Test
    fun createRadioStations_mergesLocalFavoritesAndPlayingFlags() {
        val links = List(250) { "http://stream/$it" }

        // Local station with ID matching "KISS FM" (id = 2)
        val local = listOf(
            RadioStation(2, 0, "KISS FM", "100.3", "NAIROBI", "", isFavorite = true, isPlaying = true)
        )

        val stations = RadioStationsHelper.createRadioStations(links, local)

        val kissFm = stations.firstOrNull { it.id == 2 }
        assertThat(kissFm).isNotNull()
        assertThat(kissFm?.stationName).isEqualTo("KISS FM")
        assertThat(kissFm?.isFavorite).isTrue()
        assertThat(kissFm?.isPlaying).isTrue()
    }
}
