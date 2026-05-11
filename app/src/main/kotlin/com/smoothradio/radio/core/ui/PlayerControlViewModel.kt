package com.smoothradio.radio.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.domain.model.ToastType
import com.smoothradio.radio.core.domain.repository.EqualizerRepository
import com.smoothradio.radio.core.domain.repository.PlaybackStateRepository
import com.smoothradio.radio.core.domain.repository.RadioRepository
import com.smoothradio.radio.core.domain.usecase.CanShowAdUseCase
import com.smoothradio.radio.core.domain.usecase.RecordAdShownUseCase
import com.smoothradio.radio.core.domain.usecase.SyncAdSettingsUseCase
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
    stateRepository: PlaybackStateRepository,
    private val equalizerRepository: EqualizerRepository,
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

    private val _toastMessage = MutableSharedFlow<ToastType>()
    val toastMessage: SharedFlow<ToastType> = _toastMessage.asSharedFlow()

    val eqBandLevels: StateFlow<Map<Int, Short>> = equalizerRepository.getBandLevelsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    init {
        initialize()
    }

    private fun initialize() {
        syncAdSettings()
        viewModelScope.launch {
            _canShowAd.value = canShowAdUseCase()
        }
        viewModelScope.launch {
            radioRepository.playingStation.collect { station ->
                // Filter out transient nulls during station swaps in the DB
                if (station != null) {
                    _playingStation.value = station
                }
            }
        }
    }

    fun showToast(toastType: ToastType) {
        viewModelScope.launch {
            _toastMessage.emit(toastType)
        }
    }

    fun requestPlayStation(station: RadioStation) {
        viewModelScope.launch {
            _canShowAd.value = canShowAdUseCase()
            _playingStation.value = station
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

    fun setEqualizerBand(band: Int, level: Short) {
        viewModelScope.launch {
            equalizerRepository.saveBandLevel(band, level)
            _playCommand.emit(PlayCommand.SetEqBand(band, level))
        }
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
}

sealed class PlayCommand {
    data class PlayStation(val station: RadioStation) : PlayCommand()
    object Refresh : PlayCommand()
    object Next : PlayCommand()
    object Previous : PlayCommand()
    data class SetSleepTimer(val minutes: Int) : PlayCommand()
    data class SetEqBand(val band: Int, val level: Short) : PlayCommand()
}
