package com.smoothradio.radio.core.data.repository

import androidx.media3.common.util.UnstableApi
import com.smoothradio.radio.core.domain.repository.PlaybackStateRepository
import com.smoothradio.radio.service.StreamService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class DefaultPlaybackStateRepository @Inject constructor() : PlaybackStateRepository {
    private val _playbackState = MutableStateFlow(StreamService.StreamStates.IDLE)
    override val playbackState: StateFlow<String> = _playbackState.asStateFlow()

    private val _metadata = MutableStateFlow("")
    override val metadata: StateFlow<String> = _metadata.asStateFlow()

    private val _stationName = MutableStateFlow<String?>(null)
    override val stationName: StateFlow<String?> = _stationName.asStateFlow()

    override fun updateState(state: String) {
        _playbackState.value = state
    }

    override fun updateMetadata(title: String) {
        _metadata.value = title
    }

    override fun updateStationName(name: String?) {
        _stationName.value = name
    }
}