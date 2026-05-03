package com.smoothradio.radio.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.domain.repository.PlaybackStateRepository
import com.smoothradio.radio.core.domain.repository.RadioRepository
import com.smoothradio.radio.core.domain.usecase.CanShowAdUseCase
import com.smoothradio.radio.core.domain.usecase.RecordAdShownUseCase
import com.smoothradio.radio.core.domain.usecase.SyncAdSettingsUseCase
import com.smoothradio.radio.service.StreamService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@UnstableApi
@HiltViewModel
class PlayerControlViewModel @Inject constructor(
    private val radioRepository: RadioRepository,
    private val stateRepository: PlaybackStateRepository,
    private val canShowAdUseCase: CanShowAdUseCase,
    private val recordAdShownUseCase: RecordAdShownUseCase,
    private val syncAdSettingsUseCase: SyncAdSettingsUseCase
) : ViewModel() {

    private val _playCommand = MutableSharedFlow<PlayCommand>()
    val playCommand: SharedFlow<PlayCommand> = _playCommand.asSharedFlow()

    val playbackState: StateFlow<String> = stateRepository.playbackState
    val metadata: StateFlow<String> = stateRepository.metadata

    private val _canShowAd = MutableStateFlow(false)
    val canShowAd: StateFlow<Boolean> = _canShowAd.asStateFlow()

    private val _playingStation = MutableStateFlow<RadioStation?>(null)
    val playingStation: StateFlow<RadioStation?> = _playingStation.asStateFlow()

    private val _requestState = MutableSharedFlow<Unit>()
    val requestState: SharedFlow<Unit> = _requestState.asSharedFlow()

    private val _toastMessage = MutableSharedFlow<ToastType>()
    val toastMessage: SharedFlow<ToastType> = _toastMessage.asSharedFlow()

    init {
        syncAdSettings()
        viewModelScope.launch {
            _canShowAd.value = canShowAdUseCase()
        }
        viewModelScope.launch {
            radioRepository.playingStation.collect { station ->
                // Only update when we have a real station
                station?.let { _playingStation.value = it }
            }
        }
    }

    fun showToast(toastType: ToastType) {
        viewModelScope.launch {
            _toastMessage.emit(toastType)
        }
    }

    fun requestPlayStation(station: RadioStation) {
        val currentState = stateRepository.playbackState.value
        if (currentState != StreamService.StreamStates.PREPARING) {
            stateRepository.updateState(StreamService.StreamStates.IDLE)
        }
        viewModelScope.launch {
            _canShowAd.value = canShowAdUseCase()
            _playingStation.value = station
            _playCommand.emit(PlayCommand.PlayStation(station))
        }
    }

    fun requestRefresh() {
        val currentState = stateRepository.playbackState.value
        if (currentState != StreamService.StreamStates.PREPARING) {
            stateRepository.updateState(StreamService.StreamStates.IDLE)
        }
        viewModelScope.launch {
            _playCommand.emit(PlayCommand.Refresh)
        }
    }

    fun requestNextStation() {
        viewModelScope.launch {
            _canShowAd.value = canShowAdUseCase()
            _playCommand.emit(PlayCommand.Next)
        }
    }

    fun requestPreviousStation() {
        viewModelScope.launch {
            _canShowAd.value = canShowAdUseCase()
            _playCommand.emit(PlayCommand.Previous)
        }
    }

    fun setSleepTimer(minutes: Int) {
        viewModelScope.launch {
            _playCommand.emit(PlayCommand.SetSleepTimer(minutes))
        }
    }

    fun updatePlaybackState(state: String) {
        stateRepository.updateState(state)
    }

    fun updateMetadata(metadata: String) {
        stateRepository.updateMetadata(metadata)
    }

    fun savePlayingStationId(id: Int) {
        viewModelScope.launch {
            radioRepository.setPlayingStation(id)
        }
    }

    fun recordAdShown() {
        viewModelScope.launch {
            recordAdShownUseCase()
        }
    }

    private fun syncAdSettings() {
        viewModelScope.launch {
            syncAdSettingsUseCase()
        }
    }

    fun requestStateUpdate() {
        viewModelScope.launch {
            _requestState.emit(Unit)
        }
    }
}

sealed class PlayCommand {
    data class PlayStation(val station: RadioStation) : PlayCommand()
    object Refresh : PlayCommand()
    object Next : PlayCommand()
    object Previous : PlayCommand()
    data class SetSleepTimer(val minutes: Int) : PlayCommand()
}
