package com.smoothradio.radio.core.domain.repository

import com.smoothradio.radio.core.domain.model.StreamStates
import kotlinx.coroutines.flow.StateFlow

interface PlaybackStateRepository {
    val playbackState: StateFlow<StreamStates>
    val metadata: StateFlow<String>
    val stationName: StateFlow<String?>

    fun updateState(state: StreamStates)
    fun updateMetadata(title: String)
    fun updateStationName(name: String?)
}
