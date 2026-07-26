package com.smoothradio.radio.feature.info.domain.model

import com.smoothradio.radio.core.domain.model.ListItem

data class ChangelogItem(
    val title: String, 
    val description: String
) : ListItem
