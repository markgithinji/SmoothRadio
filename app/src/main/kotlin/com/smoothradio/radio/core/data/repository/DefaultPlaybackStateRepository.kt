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

    private val _position = MutableStateFlow(0L)
    override val position: StateFlow<Long> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _minPosition = MutableStateFlow(0L)
    override val minPosition: StateFlow<Long> = _minPosition.asStateFlow()

    private val _loadedPosition = MutableStateFlow(0L)
    override val loadedPosition: StateFlow<Long> = _loadedPosition.asStateFlow()

    override fun updateState(state: StreamStates) {
        _playbackState.value = state
    }

    override fun updateMetadata(title: String) {
        _metadata.value = title
    }

    override fun updateStationName(name: String?) {
        _stationName.value = name
    }

    override fun updatePosition(position: Long) {
        _position.value = position
    }

    override fun updateDuration(duration: Long) {
        _duration.value = duration
    }

    override fun updateMinPosition(minPos: Long) {
        _minPosition.value = minPos
    }

    override fun updateLoadedPosition(loadedPos: Long) {
        _loadedPosition.value = loadedPos
    }
}
