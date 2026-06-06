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
import kotlinx.coroutines.channels.BufferOverflow
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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class PlayerControlViewModel @Inject constructor(
    private val radioRepository: RadioRepository,
    stateRepository: PlaybackStateRepository,
    private val equalizerRepository: EqualizerRepository,
    private val canShowAdUseCase: CanShowAdUseCase,
    private val recordAdShownUseCase: RecordAdShownUseCase,
    private val syncAdSettingsUseCase: SyncAdSettingsUseCase
) : ViewModel() {

    private val _playCommand = Channel<PlayCommand>(Channel.BUFFERED)
    val playCommand: Flow<PlayCommand> = _playCommand.receiveAsFlow()

    // Flag used as a 'state guard' to:
    // 1. Mask PLAYING state as BUFFERING during transitions (prevents UI flicker/dimming).
    // 2. Lock out database emissions until they synchronize with manual user selection.
    private val _isStationChanging = MutableStateFlow(false)

    private val _stationUiState = MutableStateFlow(StationUiState(null))
    val stationUiState: StateFlow<StationUiState> = _stationUiState.asStateFlow()

    private val _playingStation = MutableStateFlow<RadioStation?>(null)
    val playingStation: StateFlow<RadioStation?> = _playingStation.asStateFlow()

    private val allStations = radioRepository.allStations.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val playbackState: StateFlow<StreamStates> = combine(
        stateRepository.playbackState,
        _isStationChanging
    ) { state, changing ->
        // If we are changing stations, force a buffering state until the old stream actually stops.
        // This keeps the UI (like the seekbar) visually active to avoid dimming "noise".
        if (changing && state is StreamStates.PLAYING) {
            StreamStates.BUFFERING
        } else {
            state
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = StreamStates.IDLE
    )

    val metadata: StateFlow<String> = stateRepository.metadata
    val position: StateFlow<Long> = stateRepository.position
    val duration: StateFlow<Long> = stateRepository.duration
    val minPosition: StateFlow<Long> = stateRepository.minPosition
    val loadedPosition: StateFlow<Long> = stateRepository.loadedPosition
    val loadingProgress: StateFlow<Float> = stateRepository.loadingProgress

    private val _canShowAd = MutableStateFlow(false)
    val canShowAd: StateFlow<Boolean> = _canShowAd.asStateFlow()

    private val _playRequests = MutableSharedFlow<RadioStation>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

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
                if (station != null) {  // Filter out transient nulls during station swaps in the DB - ensure no null stations
                    val currentUi = _stationUiState.value

                    if (station.id != currentUi.station?.id && !_isStationChanging.value) {
                        Timber.d("Syncing UI station from DB: ${station.stationName}")
                        _stationUiState.value = StationUiState(station, 0f)
                        _playingStation.value = station
                    } else if (station.id == currentUi.station?.id) {
                        _playingStation.value = station
                        _isStationChanging.value = false
                    }
                }
            }
        }

        // trigger playback with debouncing: Process only the latest station request after a period of "silence"
        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            _playRequests
                .debounce(200.milliseconds)
                .collect { station ->
                    Timber.d("Processing play request for ${station.stationName} after debounce")
                    _canShowAd.value = canShowAdUseCase()
                    _playCommand.send(PlayCommand.PlayStation(station))
                    radioRepository.setPlayingStation(station.id)
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

        _stationUiState.value = StationUiState(station, direction)
        _playingStation.value = station

        // Explicitly enter transition mode. This manual override is necessary because
        // database updates are asynchronous; we must 'trust' the UI state and ignore
        // stale DB emissions until the Repository confirms it has received this new ID.
        _isStationChanging.value = true

        // 2. Queue the heavy work via Flow pipeline (Debounced)
        _playRequests.tryEmit(station)
    }

    fun requestRefresh() {
        viewModelScope.launch {
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
