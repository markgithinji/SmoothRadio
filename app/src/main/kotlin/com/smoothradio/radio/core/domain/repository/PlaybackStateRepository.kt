package com.smoothradio.radio.core.domain.repository

import com.smoothradio.radio.core.domain.model.StreamStates
import kotlinx.coroutines.flow.StateFlow

interface PlaybackStateRepository {
    val playbackState: StateFlow<StreamStates>
    val metadata: StateFlow<String>
    val stationName: StateFlow<String?>
    val stationId: StateFlow<Int?>
    val position: StateFlow<Long>
    val duration: StateFlow<Long>
    val minPosition: StateFlow<Long>
    val loadedPosition: StateFlow<Long>
    val loadingProgress: StateFlow<Float>

    fun updateState(state: StreamStates)
    fun updateMetadata(title: String)
    fun updateStationName(name: String?)
    fun updateStationId(id: Int?)
    fun updatePosition(position: Long)
    fun updateDuration(duration: Long)
    fun updateMinPosition(minPos: Long)
    fun updateLoadedPosition(loadedPos: Long)
    fun updateLoadingProgress(progress: Float)
}
