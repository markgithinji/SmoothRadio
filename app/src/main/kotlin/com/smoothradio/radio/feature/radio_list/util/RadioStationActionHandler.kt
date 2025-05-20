package com.smoothradio.radio.feature.radio_list.util

import com.smoothradio.radio.core.domain.model.RadioStation

interface RadioStationActionHandler {
    fun onStationSelected(station: RadioStation)
    fun onToggleFavorite(station: RadioStation, isFavorite: Boolean)
    fun onRequestShowToast(message: String)
}
