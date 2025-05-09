package com.smoothradio.radio.feature.radio_list.util;

import com.smoothradio.radio.core.model.RadioStation;

import java.util.Collections;
import java.util.List;

public class StationSortHelper {

    public static void sortByName(List<RadioStation> list, boolean ascending) {
        Collections.sort(list, (s1, s2) -> {
            String name1 = s1.getStationName().replace(" ", "").toLowerCase();
            String name2 = s2.getStationName().replace(" ", "").toLowerCase();
            int result = name1.compareTo(name2);
            return ascending ? result : -result;
        });
    }

    public static void sortByName(List<RadioStation> list) {
        sortByName(list, true); // default ascending
    }
}
