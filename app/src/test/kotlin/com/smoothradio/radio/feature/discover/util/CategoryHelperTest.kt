package com.smoothradio.radio.feature.discover.util

import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.domain.model.RadioStation
import org.junit.Test

class CategoryHelperTest {

    @Test
    fun createCategories_shouldIncludeFavorites_whenFavoritesExist() {
        val stations = listOf(
            dummyStation(id = 1, isFavorite = true),
            dummyStation(id = 2, isFavorite = false)
        )

        val categories = CategoryHelper.createCategories(stations)

        val favoritesCategory = categories.find { it.id == CategoryHelper.ID_FAVORITES }
        assertThat(favoritesCategory).isNotNull()
        assertThat(favoritesCategory?.categoryRadioStationList).hasSize(1)
        assertThat(favoritesCategory?.categoryRadioStationList?.first()?.id).isEqualTo(1)
    }

    @Test
    fun createCategories_shouldNotIncludeFavorites_whenNoFavoritesExist() {
        val stations = listOf(
            dummyStation(id = 1, isFavorite = false)
        )

        val categories = CategoryHelper.createCategories(stations)

        val favoritesCategory = categories.find { it.id == CategoryHelper.ID_FAVORITES }
        assertThat(favoritesCategory).isNull()
    }

    @Test
    fun createCategories_shouldIncludePredefinedCategories_whenMatchingStationsExist() {
        // ID 1 is in "HOT & TRENDING" according to CategoryHelper
        // ID 197 is in "LIVE MIXXES" and "KIKUYU" is not here yet
        val stations = listOf(
            dummyStation(id = 1), // HOT & TRENDING
            dummyStation(id = 197) // LIVE MIXXES & HOT & TRENDING
        )

        val categories = CategoryHelper.createCategories(stations)

        assertThat(categories.any { it.label == "HOT & TRENDING" }).isTrue()
        assertThat(categories.any { it.label == "LIVE MIXXES" }).isTrue()
        
        val hotCategory = categories.first { it.label == "HOT & TRENDING" }
        assertThat(hotCategory.categoryRadioStationList).hasSize(2)
    }

    @Test
    fun createCategories_shouldNotIncludeEmptyCategories() {
        // ID 999 is not in any predefined category
        val stations = listOf(
            dummyStation(id = 999)
        )

        val categories = CategoryHelper.createCategories(stations)

        // Only "Your Favorites" could exist if isFavorite was true, but here it's false
        assertThat(categories).isEmpty()
    }

    @Test
    fun createCategories_shouldMaintainOrderOfCategories() {
        // ID 1 (HOT & TRENDING), ID 197 (LIVE MIXXES), ID 38 (KIKUYU)
        val stations = listOf(
            dummyStation(id = 38),
            dummyStation(id = 1),
            dummyStation(id = 197)
        )

        val categories = CategoryHelper.createCategories(stations)

        // HOT & TRENDING is first, LIVE MIXXES second, KIKUYU third, EDM/AMAPIANO further down
        // ID 1 is in both HOT & TRENDING and EDM/AMAPIANO
        val labels = categories.map { it.label }
        assertThat(labels).containsExactly("HOT & TRENDING", "LIVE MIXXES", "KIKUYU", "EDM/AMAPIANO").inOrder()
    }

    private fun dummyStation(id: Int, isFavorite: Boolean = false) = RadioStation(
        id = id,
        stationName = "Station $id",
        frequency = "",
        location = "",
        streamLink = "",
        isPlaying = false,
        isFavorite = isFavorite,
        orderIndex = 0
    )
}
