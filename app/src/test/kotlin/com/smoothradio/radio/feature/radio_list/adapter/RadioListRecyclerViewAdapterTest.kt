package com.smoothradio.radio.feature.radio_list.adapter

import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.feature.radio_list.ui.adapter.RadioListRecyclerViewAdapter
import com.smoothradio.radio.feature.radio_list.util.RadioStationActionHandler
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test


@RunWith(RobolectricTestRunner::class)
class RadioListRecyclerViewAdapterTest {

    private lateinit var adapter: RadioListRecyclerViewAdapter

    private val station1 = RadioStation(1, 0, "Station One", "88.1", "Nairobi", "", false, false,0)
    private val station2 = RadioStation(2, 0, "Station Two", "91.5", "Mombasa", "", true, false,1)

    private val testHandler = object : RadioStationActionHandler {
        override fun onStationSelected(station: RadioStation) {}
        override fun onToggleFavorite(station: RadioStation, isFavorite: Boolean) {}
        override fun onRequestShowToast(message: String) {}
    }

    @Before
    fun setup() {
        adapter = RadioListRecyclerViewAdapter(listOf(station1, station2), testHandler)
    }

    @Test
    fun initializes_withItemsPlusAds() {
        val count = adapter.itemCount
        assertThat(count).isGreaterThan(2)
    }

    @Test
    fun getStationAtPosition_returnsCorrectStation() {
        val firstStation = adapter.getStationAtPosition(0)
        assertThat(firstStation.stationName).isEqualTo("Station One")
    }

    @Test
    fun getItemViewType_returnsCorrectType() {
        val viewType = adapter.getItemViewType(0)
        assertThat(viewType).isEqualTo(1) // ITEM_VIEW
    }

    @Test
    fun updateStation_notifiesChange() {
        val updated = station1.copy(isPlaying = true)
        adapter.updateStation(updated)
        val position = adapter.getPositionOfStation(updated.id)
        assertThat(position).isNotEqualTo(-1)
    }

    @Test
    fun filter_withMatchingQuery_filtersList() {
        adapter.filter("Station One")
        val first = adapter.getStationAtPosition(0)
        assertThat(first.stationName).isEqualTo("Station One")
    }

    @Test
    fun setPlayingStation_updatesPlayingState() {
        adapter.setPlayingStation(2)
        val playingStation = adapter.getStationAtPosition(adapter.getPositionOfStation(2))
        assertThat(playingStation.isPlaying).isTrue()
    }

    @Test
    fun updateFavorites_showsEmptyPlaceholderWhenFavoritesEmpty() {
        val adapter = RadioListRecyclerViewAdapter(emptyList(), testHandler)

        // Set adapter to FAVORITES state so updateFavorites can take effect
        adapter.sortAndDisplay(RadioListRecyclerViewAdapter.DisplayState.FAVORITES)
        adapter.updateFavorites(emptyList())

        // Check item count is 1 (the placeholder)
        assertThat(adapter.itemCount).isEqualTo(1)

        // Check that the first item is the empty placeholder station
        val first = adapter.getStationAtPosition(0)
        assertThat(first.stationName).isEmpty()
        assertThat(first.id).isEqualTo(0)
    }


    @Test
    fun sortAndDisplay_setsCorrectOrder() {
        adapter.sortAndDisplay(RadioListRecyclerViewAdapter.DisplayState.ASCENDING)
        val names = (0 until adapter.itemCount)
            .map { adapter.getStationAtPosition(it).stationName }
            .filter { it.isNotEmpty() }

        val sorted = names.sorted()
        assertThat(names).isEqualTo(sorted)
    }
}

