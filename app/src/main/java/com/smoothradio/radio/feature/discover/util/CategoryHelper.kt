package com.smoothradio.radio.feature.discover.util;

import com.smoothradio.radio.core.model.RadioStation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import com.smoothradio.radio.core.model.Category;

import java.util.Arrays;
import java.util.Map;

public class CategoryHelper {

    // Map to store categories and their corresponding list of station IDs
    private static final Map<String, List<Integer>> categories = new LinkedHashMap<>();

    static {
        // Initialize the categories with their corresponding station IDs
        categories.put("HOT & TRENDING", Arrays.asList(
                1, 211, 228, 26, 129, 188, 153, 62, 91, 74, 75, 104, 197, 54, 30, 103, 165, 184, 193, 199, 138, 139, 142
        ));

        categories.put("LIVE MIXXES", Arrays.asList(
                197, 109, 129, 215, 152
        ));

        categories.put("KIKUYU", Arrays.asList(
                38, 90, 221, 226, 225, 145, 41, 94, 112, 4, 10, 11, 93, 32, 177, 181, 168, 185, 187, 77, 99
        ));

        categories.put("MAASAI", Arrays.asList(
                122, 194, 64, 83, 219
        ));

        categories.put("KALENJIN", Arrays.asList(
                19, 39, 227, 42, 66, 89, 101, 114, 140, 157
        ));

        categories.put("TALKS", Arrays.asList(
                9, 161, 131, 76, 204, 166, 108, 127, 85
        ));

        categories.put("SPORTS", Arrays.asList(
                183, 9
        ));

        categories.put("SEVENTH DAY ADVENTIST", Arrays.asList(
                98, 84, 137, 178
        ));

        categories.put("LUO", Arrays.asList(
                15, 220, 113, 100, 156
        ));

        categories.put("KISII", Arrays.asList(
                16, 88, 78, 80, 205, 203, 204
        ));

        categories.put("LUHYA", Arrays.asList(
                17, 92, 85, 86, 107, 116
        ));

        categories.put("KAMBA", Arrays.asList(
                18, 82, 223, 53, 71, 95
        ));

        categories.put("MERU", Arrays.asList(
                20, 65, 105, 218
        ));

        categories.put("CONTEMPORARY CHRISTIAN", Arrays.asList(
                25, 169, 55, 28, 56, 144, 27, 119
        ));

        categories.put("PRAISE & WORSHIP", Arrays.asList(
                58, 27, 213, 224, 28, 33, 29, 57, 45, 46, 51, 52, 59, 60, 96, 110, 125, 198, 158, 180, 189, 196, 151, 81, 171, 174, 178
        ));

        categories.put("ISLAM", Arrays.asList(
                34, 67, 68, 222, 87, 154, 192, 149
        ));

        categories.put("COASTAL REGION", Arrays.asList(
                23, 123, 146, 35, 36, 49, 63, 58
        ));

        categories.put("ASIAN/HINDU", Arrays.asList(
                40, 43, 124
        ));

        categories.put("EDM/AMAPIANO", Arrays.asList(
                61, 1, 133, 54, 26, 193
        ));

        categories.put("CATHOLIC", Arrays.asList(
                130, 72, 45, 198, 73
        ));

        categories.put("REGGAE", Arrays.asList(
                164, 172, 12, 138, 212
        ));

        categories.put("POKOT", Arrays.asList(
                106, 102
        ));

        categories.put("ETHIOPIAN", Arrays.asList(
                162
        ));
    }

    // Create categories by filtering radio stations based on the predefined ID lists
    public static List<Category> createCategories(List<RadioStation> radioStations) {
        List<Category> categoriesList = new ArrayList<>();

        // Loop through each category and filter the stations by ID
        for (Map.Entry<String, List<Integer>> entry : categories.entrySet()) {
            String categoryName = entry.getKey();
            List<Integer> categoryIds = entry.getValue();

            // Filter the radio stations that belong to this category by checking their ID
            List<RadioStation> categorizedStations = new ArrayList<>();
            for (RadioStation station : radioStations) {
                if (categoryIds.contains(station.getId())) {
                    categorizedStations.add(station);
                }
            }

            // Add the category with its filtered stations
            categoriesList.add(new Category(categoryName, categorizedStations));
        }

        return categoriesList;
    }
}

