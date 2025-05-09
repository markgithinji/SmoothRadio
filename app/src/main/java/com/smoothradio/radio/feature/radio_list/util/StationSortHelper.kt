package com.smoothradio.radio.feature.radio_list.util

import com.smoothradio.radio.core.model.RadioStation

object StationSortHelper {

    @JvmStatic
    fun sortByName(list: MutableList<RadioStation>, ascending: Boolean) {
        list.sortWith { s1, s2 ->
            val name1 = s1.stationName.replace(" ", "").lowercase()
            val name2 = s2.stationName.replace(" ", "").lowercase()
            val result = name1.compareTo(name2)
            if (ascending) result else -result
        }
    }

    @JvmStatic
    fun sortByName(list: MutableList<RadioStation>) {
        sortByName(list, true) // default ascending
    }
}
