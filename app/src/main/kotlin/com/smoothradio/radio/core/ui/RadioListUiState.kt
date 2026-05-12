package com.smoothradio.radio.core.ui

import com.smoothradio.radio.core.domain.model.RadioStation

data class RadioListUiState(
    val allStations: List<RadioStation> = emptyList(),
    val filteredStations: List<RadioStation> = emptyList(),
    val isGridView: Boolean = false,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false
)