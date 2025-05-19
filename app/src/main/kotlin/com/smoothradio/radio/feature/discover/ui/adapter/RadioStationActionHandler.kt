package com.smoothradio.radio.feature.discover.ui.adapter

import com.smoothradio.radio.core.model.RadioStation

interface RadioStationActionHandler {
    fun onStationSelected(station: RadioStation)
    fun onToggleFavorite(station: RadioStation, isFavorite: Boolean)
    fun onRequestShowToast(message: String)
}
