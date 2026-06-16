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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private val commandMutex = Mutex()

    private val _playCommand = Channel<PlayCommand>(Channel.BUFFERED)
    val playCommand: Flow<PlayCommand> = _playCommand.receiveAsFlow()

    private val _canShowAd = MutableStateFlow(false)
    val canShowAd: StateFlow<Boolean> = _canShowAd.asStateFlow()

    private val _toastMessage = MutableSharedFlow<ToastType>()
    val toastMessage: SharedFlow<ToastType> = _toastMessage.asSharedFlow()

    private val _stationUiState = MutableStateFlow(StationUiState(null))
    val stationUiState: StateFlow<StationUiState> = _stationUiState.asStateFlow()

    private val _playingStation = MutableStateFlow<RadioStation?>(null)
    val playingStation: StateFlow<RadioStation?> = _playingStation.asStateFlow()

    // Flag used as a 'state guard' to:
    // 1. Mask PLAYING state as BUFFERING during transitions (prevents UI flicker/dimming).
    // 2. Lock out database emissions until they synchronize with manual user selection.
    private val _isStationChanging = MutableStateFlow(false)
    val isStationChanging: StateFlow<Boolean> = _isStationChanging.asStateFlow()

    private val allStations = radioRepository.allStations.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val playbackState: StateFlow<StreamStates> = combine(
        stateRepository.playbackState,
        _isStationChanging
    ) { state, changing ->
        // If we are changing stations, force a buffering state immediately for consistent ui
        // We only allow PLAYING to break this guard. IDLE is masked to prevent flashes during service reset.
        if (changing && state != StreamStates.PLAYING) {
            StreamStates.BUFFERING
        } else {
            state
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = StreamStates.IDLE
    )

    val eqBandLevels: StateFlow<Map<Int, Short>> = equalizerRepository.getBandLevelsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    val metadata: StateFlow<String> = stateRepository.metadata
    val position: StateFlow<Long> = stateRepository.position
    val duration: StateFlow<Long> = stateRepository.duration
    val minPosition: StateFlow<Long> = stateRepository.minPosition
    val loadedPosition: StateFlow<Long> = stateRepository.loadedPosition
    val loadingProgress: StateFlow<Float> = stateRepository.loadingProgress

    init {
        initialize()
    }

    private fun initialize() {
        syncAdSettings()
        viewModelScope.launch {
            _canShowAd.value = canShowAdUseCase()
        }

        viewModelScope.launch {
            combine(
                stateRepository.playbackState,
                stateRepository.stationId,
                _playingStation
            ) { state, currentId, targetStation ->
                Triple(state, currentId, targetStation)
            }.collect { (state, currentId, targetStation) ->
                // Allow stable states (PLAYING, PAUSED, IDLE, ENDED) to clear the guard
                // ONLY IF the service has confirmed the station ID match.
                val isStableState =
                    state != StreamStates.PREPARING && state != StreamStates.BUFFERING

                if (isStableState && currentId == targetStation?.id) {
                    if (_isStationChanging.value) {
                        _isStationChanging.value = false
                    }
                }
            }
        }

        viewModelScope.launch {
            radioRepository.playingStation.collect { station ->
                if (station != null) {
                    val currentUi = _stationUiState.value

                    if (station.id != currentUi.station?.id && !_isStationChanging.value) {
                        // Trust the Database only if not currently clicking something (Bluetooth/Startup path) (ie initial load)
                        _stationUiState.value = StationUiState(station, 0f)
                        _playingStation.value = station
                    } else if (station.id == currentUi.station?.id) {
                        // Sync data once the Database finally catches up to our manual selection (Manual path)
                        _playingStation.value = station
                    }
                }
            }
        }
    }

    fun showToast(toastType: ToastType) {
        viewModelScope.launch {
            _toastMessage.emit(toastType)
        }
    }

    fun requestPlayStation(station: RadioStation, direction: Float = 0f) {
        if (_playingStation.value?.id == station.id) return togglePlayPause()

        // RESET STATE: Immediately reset the repository state to PREPARING
        // and CLEAR the station name/id to prevent guard-clearance from stale events.
        resetPlaybackRepositoryState()
        stateRepository.updateMetadata("")

        _stationUiState.value = StationUiState(station, direction)
        _playingStation.value = station

        // Explicitly enter transition mode.
        _isStationChanging.value = true

        playStationInternal(station)
    }

    private fun playStationInternal(station: RadioStation) {
        viewModelScope.launch {
            commandMutex.withLock {
                _canShowAd.value = canShowAdUseCase()
                _playCommand.send(PlayCommand.PlayStation(station))
                radioRepository.setPlayingStation(station.id)
            }
        }
    }

    private fun resetPlaybackRepositoryState() {
        stateRepository.updateState(StreamStates.PREPARING)
        stateRepository.updateStationName(null)
        stateRepository.updateStationId(null)
        stateRepository.updateLoadingProgress(0f)
    }

    fun requestRefresh() {
        viewModelScope.launch {
            _canShowAd.value = canShowAdUseCase()

            // RESET STATE for refresh to show immediate feedback
            resetPlaybackRepositoryState()
            _isStationChanging.value = true

            _playCommand.send(PlayCommand.Refresh)
        }
    }

    fun requestNextStation() {
        val stations = allStations.value
        if (stations.isEmpty()) return

        val current = _playingStation.value
        val currentIndex = stations.indexOfFirst { it.id == current?.id }
        val nextIndex = when {
            currentIndex == -1 -> 0
            currentIndex < stations.lastIndex -> currentIndex + 1
            else -> 0
        }

        requestPlayStation(stations[nextIndex], direction = 1f)
    }

    fun requestPreviousStation() {
        val stations = allStations.value
        if (stations.isEmpty()) return

        val current = _playingStation.value
        val currentIndex = stations.indexOfFirst { it.id == current?.id }
        val prevIndex = when {
            currentIndex == -1 -> stations.lastIndex
            currentIndex > 0 -> currentIndex - 1
            else -> stations.lastIndex
        }

        requestPlayStation(stations[prevIndex], direction = -1f)
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

    fun seekTo(position: Long) {
        viewModelScope.launch {
            _playCommand.send(PlayCommand.SeekTo(position))
        }
    }

    fun seekBack() {
        viewModelScope.launch {
            _playCommand.send(PlayCommand.SeekBack)
        }
    }

    fun seekForward() {
        viewModelScope.launch {
            _playCommand.send(PlayCommand.SeekForward)
        }
    }

    fun togglePlayPause() {
        viewModelScope.launch {
            if (playbackState.value != StreamStates.PLAYING &&
                playbackState.value != StreamStates.BUFFERING &&
                playbackState.value != StreamStates.PREPARING
            ) {
                _canShowAd.value = canShowAdUseCase()
            }
            // If the user manually toggles while we are in a "changing" state,
            // we should clear the guard to allow the IDLE/PAUSED state to show.
            _isStationChanging.value = false
            _playCommand.send(PlayCommand.TogglePlayPause)
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

data class StationUiState(
    val station: RadioStation?,
    val swipeDirection: Float = 0f
)

sealed class PlayCommand {
    data class PlayStation(val station: RadioStation) : PlayCommand()
    object Refresh : PlayCommand()
    object TogglePlayPause : PlayCommand()
    data class SetSleepTimer(val minutes: Int) : PlayCommand()
    data class SetEqBand(val band: Int, val level: Short) : PlayCommand()
    data class SeekTo(val position: Long) : PlayCommand()
    object SeekBack : PlayCommand()
    object SeekForward : PlayCommand()
}
