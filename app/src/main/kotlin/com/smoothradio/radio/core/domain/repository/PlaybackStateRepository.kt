package com.smoothradio.radio.core.domain.repository

import androidx.media3.common.util.UnstableApi
import com.smoothradio.radio.service.StreamService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class PlaybackStateRepository @Inject constructor() {
    private val _playbackState = MutableStateFlow(StreamService.StreamStates.IDLE)
    val playbackState: StateFlow<String> = _playbackState.asStateFlow()

    private val _metadata = MutableStateFlow("")
    val metadata: StateFlow<String> = _metadata.asStateFlow()

    fun updateState(state: String) {
        _playbackState.value = state
    }

    fun updateMetadata(title: String) {
        _metadata.value = title
    }
}
