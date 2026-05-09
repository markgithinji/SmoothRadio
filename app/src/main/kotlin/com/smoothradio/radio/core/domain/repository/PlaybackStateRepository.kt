package com.smoothradio.radio.core.domain.repository

import androidx.media3.common.util.UnstableApi
import com.smoothradio.radio.service.StreamService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

interface PlaybackStateRepository {
    val playbackState: StateFlow<String>
    val metadata: StateFlow<String>
    val stationName: StateFlow<String?>

    fun updateState(state: String)
    fun updateMetadata(title: String)
    fun updateStationName(name: String?)
}