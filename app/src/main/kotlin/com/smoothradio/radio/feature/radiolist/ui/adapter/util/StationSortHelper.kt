package com.smoothradio.radio.feature.radiolist.ui.adapter.util

import com.smoothradio.radio.core.domain.model.RadioStation

object StationSortHelper {

    fun sortByName(list: MutableList<RadioStation>, ascending: Boolean = true) {
        val comparator = Comparator<RadioStation> { a, b ->
            val name1 = a.stationName.normalizeForSort()
            val name2 = b.stationName.normalizeForSort()
            name1.compareTo(name2) * if (ascending) 1 else -1
        }
        list.sortWith(comparator)
    }

    private fun String.normalizeForSort(): String =
        replace(" ", "").lowercase()
}