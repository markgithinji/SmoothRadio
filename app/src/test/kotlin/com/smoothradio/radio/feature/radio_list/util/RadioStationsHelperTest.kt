package com.smoothradio.radio.feature.radio_list.util

import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.data.repository.FakeRadioLinkRepository
import com.smoothradio.radio.core.util.Resource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RadioStationsHelperTest {
    private val fakeLinkRepository = FakeRadioLinkRepository()

    @Test
    fun createRadioStations_generatesCorrectStationsFromLinks() = runTest {
        val resource = fakeLinkRepository.getRemoteStreamLinksFlow().first()

        val links = (resource as? Resource.Success)?.data
            ?: error("Expected Resource.Success but got $resource")

        val stations = RadioStationsHelper.createRadioStations(links)

        assertThat(stations).isNotEmpty()
        // Check the first and last station names and links
        assertThat(stations.first().stationName).isEqualTo("HOPE FM")
        assertThat(stations.first().streamLink).isEqualTo("https://stream0.com")
        assertThat(stations.last().stationName).isEqualTo("HABESHINGA MUSIC")
        assertThat(stations.last().streamLink).isEqualTo("https://stream201.com")
        assertThat(stations.size).isEqualTo(216)// 216 stations in total
        // Check station at last server index, which should be KWITU FM of id = 231
        assertThat(stations.first { it.id == 231 }.stationName).isEqualTo("KWITU FM")
        assertThat(stations.first { it.id == 231 }.streamLink).isEqualTo("https://stream231.com")
    }
}
