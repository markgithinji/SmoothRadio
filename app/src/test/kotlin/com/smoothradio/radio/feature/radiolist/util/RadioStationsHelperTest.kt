package com.smoothradio.radio.feature.radiolist.util

import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.data.repository.FakeFirebaseRepository
import com.smoothradio.radio.core.util.Resource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RadioStationsHelperTest {
    private val fakeLinkRepository = FakeFirebaseRepository()

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
        
        // Count total stations added in code
        assertThat(stations.size).isEqualTo(213) 
        
        // Check station at specific index mapping
        assertThat(stations.first { it.id == 231 }.stationName).isEqualTo("KWITU FM")
        assertThat(stations.first { it.id == 231 }.streamLink).isEqualTo("https://stream231.com")
    }

    @Test
    fun createRadioStations_withEmptyLinks_usesEmptyStringForStreams() {
        val stations = RadioStationsHelper.createRadioStations(emptyList())
        
        assertThat(stations).isNotEmpty()
        assertThat(stations.first().streamLink).isEmpty()
        assertThat(stations.last().streamLink).isEmpty()
    }

    @Test
    fun createRadioStations_assignsIncrementalOrderIndex() {
        val stations = RadioStationsHelper.createRadioStations(emptyList())
        
        stations.forEachIndexed { index, radioStation ->
            assertThat(radioStation.orderIndex).isEqualTo(index)
        }
    }

    @Test
    fun createRadioStations_verifiesSpecificStationData() {
        val links = List(232) { "http://link-$it" }
        val stations = RadioStationsHelper.createRadioStations(links)
        
        // Verify Radio 47 (id 228)
        val radio47 = stations.first { it.id == 228 }
        assertThat(radio47.stationName).isEqualTo("RADIO 47")
        assertThat(radio47.frequency).isEqualTo("103.0")
        assertThat(radio47.location).isEqualTo("NAIROBI")
        assertThat(radio47.streamLink).isEqualTo("http://link-228")
        
        // Verify Classic 105 (id 8)
        val classic105 = stations.first { it.id == 8 }
        assertThat(classic105.stationName).isEqualTo("CLASSIC 105")
        assertThat(classic105.streamLink).isEqualTo("http://link-8")
    }
}
