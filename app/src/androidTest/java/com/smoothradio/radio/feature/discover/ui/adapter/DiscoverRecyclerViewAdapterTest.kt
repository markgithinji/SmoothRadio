package com.smoothradio.radio.feature.discover.ui.adapter

import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.R
import com.smoothradio.radio.core.domain.model.Category
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.feature.discover.util.RadioStationActionHandler
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DiscoverRecyclerViewAdapterTest {

    private lateinit var adapter: DiscoverRecyclerViewAdapter
    private lateinit var actionHandler: RadioStationActionHandler
    private lateinit var testCategory: Category

    @Before
    fun setUp() {
        actionHandler = mock()

        val radioStation = RadioStation(
            id = 1,
            logoResource = R.drawable.hopefm,
            stationName = "Smooth FM",
            frequency = "99.9",
            location = "Nairobi",
            streamLink = "https://stream.smoothradio.com",
            isPlaying = false,
            isFavorite = false
        )

        testCategory = Category(
            label = "Top Stations",
            categoryRadioStationList = listOf(radioStation)
        )

        adapter = DiscoverRecyclerViewAdapter(listOf(testCategory), actionHandler)
    }

    @Test
    fun shouldCreateViewHolderCorrectly() {
        val parent = FrameLayout(ApplicationProvider.getApplicationContext())
        val viewHolder = adapter.onCreateViewHolder(parent, 0)

        assertThat(viewHolder.binding.tvCategoryLabel).isNotNull()
    }

    @Test
    fun shouldBindCategoryLabelToViewHolder() {
        val parent = FrameLayout(ApplicationProvider.getApplicationContext())
        val viewHolder = adapter.onCreateViewHolder(parent, 0)

        adapter.onBindViewHolder(viewHolder, 0)

        assertThat(viewHolder.binding.tvCategoryLabel.text.toString()).isEqualTo("Top Stations")
    }

    @Test
    fun shouldSetupNestedRecyclerViewAdapterCorrectly() {
        val parent = FrameLayout(ApplicationProvider.getApplicationContext())
        val viewHolder = adapter.onCreateViewHolder(parent, 0)

        adapter.onBindViewHolder(viewHolder, 0)

        val nestedRecyclerView = viewHolder.binding.rvCategory

        assertThat(nestedRecyclerView.adapter).isNotNull()
        assertThat(nestedRecyclerView.layoutManager).isNotNull()
        assertThat(nestedRecyclerView.adapter).isInstanceOf(CategoryRecyclerViewAdapter::class.java)
    }

    @Test
    fun shouldReturnCorrectItemCount() {
        assertThat(adapter.itemCount).isEqualTo(1)
    }
}
