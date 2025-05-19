package com.smoothradio.radio.feature.radio_list.ui.adapter

import com.smoothradio.radio.core.model.RadioStation

interface RadioStationActionHandler {
    fun onStationSelected(station: RadioStation)
    fun onToggleFavorite(station: RadioStation, isFavorite: Boolean)
    fun onRequestHideKeyboard()
    fun onRequestShowToast(message: String)
}
