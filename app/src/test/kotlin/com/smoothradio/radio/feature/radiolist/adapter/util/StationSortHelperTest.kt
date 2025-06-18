package com.smoothradio.radio.feature.radiolist.adapter.util

import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.feature.radiolist.ui.adapter.util.StationSortHelper
import org.junit.Test

class StationSortHelperTest {

    private fun station(name: String) = RadioStation(
        id = 0,
        logoResource = 0,
        stationName = name,
        frequency = "",
        location = "",
        streamLink = "",
        isFavorite = false,
        isPlaying = false,
        orderIndex = 0
    )

    @Test
    fun sortByName_ascending_sortsAlphabetically() {
        val list = mutableListOf(
            station("Zebra Radio"),
            station("Alpha FM"),
            station("echo FM")
        )

        StationSortHelper.sortByName(list, ascending = true)

        assertThat(list.map { it.stationName }).containsExactly(
            "Alpha FM", "echo FM", "Zebra Radio"
        ).inOrder()
    }

    @Test
    fun sortByName_descending_sortsReverseAlphabetically() {
        val list = mutableListOf(
            station("Zebra Radio"),
            station("Alpha FM"),
            station("echo FM")
        )

        StationSortHelper.sortByName(list, ascending = false)

        assertThat(list.map { it.stationName }).containsExactly(
            "Zebra Radio", "echo FM", "Alpha FM"
        ).inOrder()
    }

    @Test
    fun sortByName_ignoresSpacesAndCase() {
        val list = mutableListOf(
            station("Echo FM"),
            station("  alpha   fm "),
            station("ZEBRA RADIO")
        )

        StationSortHelper.sortByName(list)

        assertThat(list.map { it.stationName }).containsExactly(
            "  alpha   fm ", "Echo FM", "ZEBRA RADIO"
        ).inOrder()
    }
}
