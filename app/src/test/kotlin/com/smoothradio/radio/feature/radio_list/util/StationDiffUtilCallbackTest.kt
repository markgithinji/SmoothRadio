package com.smoothradio.radio.feature.radio_list.util

import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.model.AdItem
import com.smoothradio.radio.core.model.ListItem
import com.smoothradio.radio.core.model.RadioStation
import org.junit.Test

class StationDiffUtilCallbackTest {

    private fun station(
        id: Int,
        url: String = "http://stream/$id",
        isFavorite: Boolean = false,
        isPlaying: Boolean = false
    ): RadioStation {
        return RadioStation(
            id = id,
            logoResource = 0,
            stationName = "Station $id",
            frequency = "100.0",
            location = "Nairobi",
            streamLink = url,
            isFavorite = isFavorite,
            isPlaying = isPlaying
        )
    }

    @Test
    fun sameStation_sameData_areContentsSameTrue() {
        val oldList: List<ListItem> = listOf(station(1))
        val newList: List<ListItem> = listOf(station(1))

        val diff = StationDiffUtilCallback(oldList, newList)

        assertThat(diff.areItemsTheSame(0, 0)).isTrue()
        assertThat(diff.areContentsTheSame(0, 0)).isTrue()
    }

    @Test
    fun sameStation_favoriteChanged_areContentsSameFalse() {
        val oldList: List<ListItem> = listOf(station(1, isFavorite = false))
        val newList: List<ListItem> = listOf(station(1, isFavorite = true))

        val diff = StationDiffUtilCallback(oldList, newList)

        assertThat(diff.areItemsTheSame(0, 0)).isTrue()
        assertThat(diff.areContentsTheSame(0, 0)).isFalse()
    }

    @Test
    fun sameStation_playingChanged_areContentsSameFalse() {
        val oldList: List<ListItem> = listOf(station(1, isPlaying = false))
        val newList: List<ListItem> = listOf(station(1, isPlaying = true))

        val diff = StationDiffUtilCallback(oldList, newList)

        assertThat(diff.areItemsTheSame(0, 0)).isTrue()
        assertThat(diff.areContentsTheSame(0, 0)).isFalse()
    }

    @Test
    fun differentStations_areItemsSameFalse() {
        val oldList: List<ListItem> = listOf(station(1))
        val newList: List<ListItem> = listOf(station(2))

        val diff = StationDiffUtilCallback(oldList, newList)

        assertThat(diff.areItemsTheSame(0, 0)).isFalse()
        assertThat(diff.areContentsTheSame(0, 0)).isFalse()
    }

    @Test
    fun adItems_areAlwaysSame() {
        val oldList: List<ListItem> = listOf(AdItem())
        val newList: List<ListItem> = listOf(AdItem())

        val diff = StationDiffUtilCallback(oldList, newList)

        assertThat(diff.areItemsTheSame(0, 0)).isTrue()
        assertThat(diff.areContentsTheSame(0, 0)).isTrue()
    }

    @Test
    fun mixedTypes_areNotSame() {
        val oldList: List<ListItem> = listOf(station(1))
        val newList: List<ListItem> = listOf(AdItem())

        val diff = StationDiffUtilCallback(oldList, newList)

        assertThat(diff.areItemsTheSame(0, 0)).isFalse()
        assertThat(diff.areContentsTheSame(0, 0)).isFalse()
    }
}
