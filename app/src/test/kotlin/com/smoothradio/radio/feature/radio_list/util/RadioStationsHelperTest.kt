package com.smoothradio.radio.feature.radio_list.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RadioStationsHelperTest {

    @Test
    fun createRadioStations_generatesStationsFromLinks() {
        // Given a list of 231 dummy links
        val links = List(231) { "http://stream/$it" }

        val stations = RadioStationsHelper.createRadioStations(links)

        assertThat(stations[0].stationName).isEqualTo("HOPE FM")
        assertThat(stations[0].streamLink).isEqualTo("http://stream/0")
        assertThat(stations.find { it.id == 228 }?.stationName).isEqualTo("RADIO 47")
        assertThat(stations.size).isEqualTo(229) // We have 231 stations currently
    }
}
