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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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

    val playingStation: StateFlow<RadioStation?> = radioRepository.playingStation
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    init {
        syncAdSettings()
    }

    fun requestPlayStation(station: RadioStation) {
        viewModelScope.launch {
            // Refresh ad status before playing
            val canShow = canShowAdUseCase()
            _canShowAd.value = canShow

            _playCommand.emit(PlayCommand.PlayStation(station))
        }
    }

    fun requestRefresh() {
        viewModelScope.launch {
            _playCommand.emit(PlayCommand.Refresh)
        }
    }

    fun updatePlaybackState(state: String) {
        _playbackState.value = state
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

    fun syncAdSettings() {
        viewModelScope.launch {
            syncAdSettingsUseCase()
        }
    }
}

sealed class PlayCommand {
    data class PlayStation(val station: RadioStation) : PlayCommand()
    object Refresh : PlayCommand()
}