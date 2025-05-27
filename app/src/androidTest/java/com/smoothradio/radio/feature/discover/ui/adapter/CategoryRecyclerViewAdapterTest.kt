package com.smoothradio.radio.feature.discover.ui.adapter

import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.R
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.feature.discover.util.RadioStationActionHandler
import com.smoothradio.radio.service.StreamService
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify


@RunWith(AndroidJUnit4::class) //
class CategoryRecyclerViewAdapterTest {

    private lateinit var adapter: CategoryRecyclerViewAdapter
    private lateinit var actionHandler: RadioStationActionHandler
    private lateinit var testStation: RadioStation
    private lateinit var recyclerView: RecyclerView

    @Before
    fun setUp() {
        actionHandler = mock()
        testStation = RadioStation(
            id = 1,
            logoResource = R.drawable.hopefm,
            stationName = "Smooth FM",
            frequency = "99.9",
            location = "Nairobi",
            streamLink = "https://stream.smoothradio.com",
            isPlaying = false,
            isFavorite = false,
            orderIndex = 0
        )
        adapter = CategoryRecyclerViewAdapter(listOf(testStation), actionHandler)

        recyclerView = RecyclerView(ApplicationProvider.getApplicationContext())
        recyclerView.adapter = adapter
    }

    @Test
    fun shouldCreateViewHolderCorrectly() {
        val parent = FrameLayout(ApplicationProvider.getApplicationContext())
        val viewHolder = adapter.onCreateViewHolder(parent, 0)

        assertThat(viewHolder.binding.tvCategoryChannelName).isNotNull()
    }

    @Test
    fun shouldBindStationNameAndLogo() {
        val parent = FrameLayout(ApplicationProvider.getApplicationContext())
        val viewHolder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(viewHolder, 0)

        assertThat(viewHolder.binding.tvCategoryChannelName.text.toString()).isEqualTo("Smooth FM")
        assertThat(viewHolder.binding.ivCategoryLogo.drawable).isNotNull()
    }

    @Test
    fun shouldHandlePlayButtonClick() {
        val parent = FrameLayout(ApplicationProvider.getApplicationContext())
        val viewHolder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(viewHolder, 0)

        viewHolder.binding.ivCategoryPlay.performClick()

        verify(actionHandler).onStationSelected(testStation)
    }

    @Test
    fun shouldToggleFavoriteOnFavoriteButtonClick() {
        val parent = FrameLayout(ApplicationProvider.getApplicationContext())
        val viewHolder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(viewHolder, 0)

        // Initially not favorite
        assertThat(testStation.isFavorite).isFalse()

        viewHolder.binding.ivCategoryFavourite.performClick()

        verify(actionHandler).onToggleFavorite(testStation, true)
        verify(actionHandler).onRequestShowToast("Added to favorites: Smooth FM")
    }

    @Test
    fun shouldUpdatePlayingStateWhenStationSelected() {
        assertThat(testStation.isPlaying).isFalse()

        adapter.setSelectedStationWithState(testStation, StreamService.StreamStates.PLAYING)

        assertThat(testStation.isPlaying).isTrue()
    }
}
