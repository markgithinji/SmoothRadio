package com.smoothradio.radio.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.domain.model.StreamStates
import com.smoothradio.radio.core.domain.model.ToastType
import com.smoothradio.radio.core.domain.repository.EqualizerRepository
import com.smoothradio.radio.core.domain.repository.PlaybackStateRepository
import com.smoothradio.radio.core.domain.repository.RadioRepository
import com.smoothradio.radio.core.domain.usecase.CanShowAdUseCase
import com.smoothradio.radio.core.domain.usecase.RecordAdShownUseCase
import com.smoothradio.radio.core.domain.usecase.SyncAdSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerControlViewModel @Inject constructor(
    private val radioRepository: RadioRepository,
    private val stateRepository: PlaybackStateRepository,
    private val equalizerRepository: EqualizerRepository,
    private val canShowAdUseCase: CanShowAdUseCase,
    private val recordAdShownUseCase: RecordAdShownUseCase,
    private val syncAdSettingsUseCase: SyncAdSettingsUseCase
) : ViewModel() {

    private val _playCommand = Channel<PlayCommand>(Channel.BUFFERED)
    val playCommand: Flow<PlayCommand> = _playCommand.receiveAsFlow()

    // Flag to mask the "PLAYING" state during station transitions
    private val _isStationChanging = MutableStateFlow(false)

    val playbackState: StateFlow<StreamStates> = combine(
        stateRepository.playbackState,
        _isStationChanging
    ) { state, changing ->
        // If we are changing stations, force a loading state until the old stream actually stops
        if (changing && state is StreamStates.PLAYING) {
            StreamStates.IDLE
        } else {
            state
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = StreamStates.IDLE
    )

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
        viewModelScope.launch {
            stateRepository.playbackState.collect { state ->
                // Reset the changing flag once the underlying state is no longer PLAYING
                if (state !is StreamStates.PLAYING) {
                    _isStationChanging.value = false
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
            // Mask the "playing" state for the new station until the old one stops
            val isNewStation = _playingStation.value?.id != station.id
            if (isNewStation && stateRepository.playbackState.value is StreamStates.PLAYING) {
                _isStationChanging.value = true
            }

            _canShowAd.value = canShowAdUseCase()
            _playingStation.value = station
            _playCommand.send(PlayCommand.PlayStation(station))
            savePlayingStationId(station.id)
        }
    }

    fun requestRefresh() {
        viewModelScope.launch {
            _playCommand.send(PlayCommand.Refresh)
        }
    }

    fun requestNextStation() {
        viewModelScope.launch {
            val stations = radioRepository.allStations.first()
            if (stations.isEmpty()) return@launch

            val current = _playingStation.value
            val currentIndex = stations.indexOfFirst { it.id == current?.id }
            val nextIndex = when {
                currentIndex == -1 -> 0
                currentIndex < stations.lastIndex -> currentIndex + 1
                else -> 0
            }

            val nextStation = stations[nextIndex]
            if (nextStation.id == current?.id) return@launch

            requestPlayStation(nextStation)
        }
    }

    fun requestPreviousStation() {
        viewModelScope.launch {
            val stations = radioRepository.allStations.first()
            if (stations.isEmpty()) return@launch

            val current = _playingStation.value
            val currentIndex = stations.indexOfFirst { it.id == current?.id }
            val prevIndex = when {
                currentIndex == -1 -> stations.lastIndex
                currentIndex > 0 -> currentIndex - 1
                else -> stations.lastIndex
            }

            val prevStation = stations[prevIndex]
            if (prevStation.id == current?.id) return@launch

            requestPlayStation(prevStation)
        }
    }

    fun setSleepTimer(minutes: Int) {
        viewModelScope.launch {
            _playCommand.send(PlayCommand.SetSleepTimer(minutes))
        }
    }

    fun setEqualizerBand(band: Int, level: Short) {
        viewModelScope.launch {
            equalizerRepository.saveBandLevel(band, level)
            _playCommand.send(PlayCommand.SetEqBand(band, level))
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
    data class SetSleepTimer(val minutes: Int) : PlayCommand()
    data class SetEqBand(val band: Int, val level: Short) : PlayCommand()
}
