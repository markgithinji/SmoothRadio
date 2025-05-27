package com.smoothradio.radio.feature.discover.util

import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.domain.model.Category
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.feature.discover.ui.adapter.util.CategoryDiffUtilCallback
import kotlin.test.Test

class CategoryDiffUtilCallbackTest {

    @Test
    fun areItemsTheSame_returnsTrueForSameLabel() {
        val oldList = listOf(Category("Popular", listOf()))
        val newList = listOf(Category("Popular", listOf()))
        val callback = CategoryDiffUtilCallback(oldList, newList)

        assertThat(callback.areItemsTheSame(0, 0)).isTrue()
    }

    @Test
    fun areContentsTheSame_returnsFalseWhenStationsChange() {
        val oldCategory =
            Category("Popular", listOf(RadioStation(1, 0, "A", "101", "Loc", "url", false, false,0)))
        val newCategory =
            Category("Popular", listOf(RadioStation(2, 0, "B", "102", "Loc", "url", false, false,1)))

        val callback = CategoryDiffUtilCallback(listOf(oldCategory), listOf(newCategory))

        assertThat(callback.areContentsTheSame(0, 0)).isFalse()
    }

    @Test
    fun areItemsTheSame_returnsFalseForDifferentLabels() {
        val callback = CategoryDiffUtilCallback(
            listOf(Category("Popular", listOf())),
            listOf(Category("Recent", listOf()))
        )

        assertThat(callback.areItemsTheSame(0, 0)).isFalse()
    }
}
