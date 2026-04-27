package com.smoothradio.radio.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smoothradio.radio.core.domain.model.RadioStation
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

@HiltViewModel
class PlayerControlViewModel @Inject constructor(
    private val radioRepository: RadioRepository,
    private val canShowAdUseCase: CanShowAdUseCase,
    private val recordAdShownUseCase: RecordAdShownUseCase,
    private val syncAdSettingsUseCase: SyncAdSettingsUseCase
) : ViewModel() {

    private val _playCommand = MutableSharedFlow<PlayCommand>()
    val playCommand: SharedFlow<PlayCommand> = _playCommand.asSharedFlow()

    private val _playbackState = MutableStateFlow(StreamService.StreamStates.IDLE)
    val playbackState: StateFlow<String> = _playbackState.asStateFlow()

    private val _canShowAd = MutableStateFlow(false)
    val canShowAd: StateFlow<Boolean> = _canShowAd.asStateFlow()

    private val _playingStation = MutableStateFlow<RadioStation?>(null)
    val playingStation: StateFlow<RadioStation?> = _playingStation.asStateFlow()

    private val _metadata = MutableStateFlow("")
    val metadata: StateFlow<String> = _metadata.asStateFlow()

    private val _requestState = MutableSharedFlow<Unit>()
    val requestState: SharedFlow<Unit> = _requestState.asSharedFlow()

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

    fun requestPlayStation(station: RadioStation) {
        viewModelScope.launch {
            _canShowAd.value = canShowAdUseCase()
            _playCommand.emit(PlayCommand.PlayStation(station))
        }
    }

    fun requestRefresh() {
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
        _playbackState.value = state
    }

    fun updateMetadata(metadata: String) {
        _metadata.value = metadata
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