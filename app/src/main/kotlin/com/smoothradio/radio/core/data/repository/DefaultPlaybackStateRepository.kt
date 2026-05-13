package com.smoothradio.radio.core.data.repository

import com.smoothradio.radio.core.domain.model.StreamStates
import com.smoothradio.radio.core.domain.repository.PlaybackStateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultPlaybackStateRepository @Inject constructor() : PlaybackStateRepository {
    private val _playbackState = MutableStateFlow<StreamStates>(StreamStates.IDLE)
    override val playbackState: StateFlow<StreamStates> = _playbackState.asStateFlow()

    private val _metadata = MutableStateFlow("")
    override val metadata: StateFlow<String> = _metadata.asStateFlow()

    private val _stationName = MutableStateFlow<String?>(null)
    override val stationName: StateFlow<String?> = _stationName.asStateFlow()

    override fun updateState(state: StreamStates) {
        _playbackState.value = state
    }

    override fun updateMetadata(title: String) {
        _metadata.value = title
    }

    override fun updateStationName(name: String?) {
        _stationName.value = name
    }
}
