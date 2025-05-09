package com.smoothradio.radio.feature.discover.util

import com.smoothradio.radio.core.model.Category
import com.smoothradio.radio.core.model.RadioStation

object CategoryHelper {

    private val categories: Map<String, List<Int>> = linkedMapOf(
        "HOT & TRENDING" to listOf(
            1, 211, 228, 26, 129, 188, 153, 62, 91, 74, 75, 104, 197, 54, 30, 103, 165, 184, 193, 199, 138, 139, 142
        ),
        "LIVE MIXXES" to listOf(
            197, 109, 129, 215, 152
        ),
        "KIKUYU" to listOf(
            38, 90, 221, 226, 225, 145, 41, 94, 112, 4, 10, 11, 93, 32, 177, 181, 168, 185, 187, 77, 99
        ),
        "MAASAI" to listOf(
            122, 194, 64, 83, 219
        ),
        "KALENJIN" to listOf(
            19, 39, 227, 42, 66, 89, 101, 114, 140, 157
        ),
        "TALKS" to listOf(
            9, 161, 131, 76, 204, 166, 108, 127, 85
        ),
        "SPORTS" to listOf(
            183, 9
        ),
        "SEVENTH DAY ADVENTIST" to listOf(
            98, 84, 137, 178
        ),
        "LUO" to listOf(
            15, 220, 113, 100, 156
        ),
        "KISII" to listOf(
            16, 88, 78, 80, 205, 203, 204
        ),
        "LUHYA" to listOf(
            17, 92, 85, 86, 107, 116
        ),
        "KAMBA" to listOf(
            18, 82, 223, 53, 71, 95
        ),
        "MERU" to listOf(
            20, 65, 105, 218
        ),
        "CONTEMPORARY CHRISTIAN" to listOf(
            25, 169, 55, 28, 56, 144, 27, 119
        ),
        "PRAISE & WORSHIP" to listOf(
            58, 27, 213, 224, 28, 33, 29, 57, 45, 46, 51, 52, 59, 60, 96, 110, 125, 198, 158, 180, 189, 196, 151, 81, 171, 174, 178
        ),
        "ISLAM" to listOf(
            34, 67, 68, 222, 87, 154, 192, 149
        ),
        "COASTAL REGION" to listOf(
            23, 123, 146, 35, 36, 49, 63, 58
        ),
        "ASIAN/HINDU" to listOf(
            40, 43, 124
        ),
        "EDM/AMAPIANO" to listOf(
            61, 1, 133, 54, 26, 193
        ),
        "CATHOLIC" to listOf(
            130, 72, 45, 198, 73
        ),
        "REGGAE" to listOf(
            164, 172, 12, 138, 212
        ),
        "POKOT" to listOf(
            106, 102
        ),
        "ETHIOPIAN" to listOf(
            162
        )
    )

    fun createCategories(radioStations: List<RadioStation>): List<Category> {
        val categoriesList = mutableListOf<Category>()

        categories.forEach { (categoryName, categoryIds) ->
            val categorizedStations = radioStations.filter { station -> categoryIds.contains(station.id) }
            categoriesList.add(Category(categoryName, categorizedStations))
        }

        return categoriesList
    }
}
