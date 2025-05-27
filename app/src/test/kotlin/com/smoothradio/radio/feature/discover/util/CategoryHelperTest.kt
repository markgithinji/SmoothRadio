package com.smoothradio.radio.feature.discover.util

import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.R
import com.smoothradio.radio.core.domain.model.RadioStation
import org.junit.Test

class CategoryHelperTest {

    private fun dummyStation(id: Int) = RadioStation(
        id = id,
        logoResource = R.drawable.hopefm,
        stationName = "Station $id",
        frequency = "100.0",
        location = "Test",
        streamLink = "url",
        isPlaying = false,
        isFavorite = false,
        orderIndex = 0
    )

    @Test
    fun shouldGroupStationByCategoryId() {
        val station = dummyStation(1) // In HOT & TRENDING and EDM/AMAPIANO
        val categories = CategoryHelper.createCategories(listOf(station))

        val categoryNames = categories.map { it.label }
        val hotTrending = categories.find { it.label == "HOT & TRENDING" }

        assertThat(categoryNames).contains("HOT & TRENDING")
        assertThat(hotTrending?.categoryRadioStationList).contains(station)
    }

    @Test
    fun shouldNotIncludeStationIfIdNotInAnyCategory() {
        val station = dummyStation(999)
        val categories = CategoryHelper.createCategories(listOf(station))

        val found = categories.any { it.categoryRadioStationList.contains(station) }
        assertThat(found).isFalse()
    }

    @Test
    fun shouldIncludeEmptyCategoriesIfNoMatches() {
        val categories = CategoryHelper.createCategories(emptyList())

        assertThat(categories).isNotEmpty()
        assertThat(categories.all { it.categoryRadioStationList.isEmpty() }).isTrue()
    }
}
