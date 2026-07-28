package com.smoothradio.radio.core.ui

import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.domain.model.StreamStates
import com.smoothradio.radio.core.domain.model.ToastType
import com.smoothradio.radio.core.data.repository.FakeAdSettingsRepository
import com.smoothradio.radio.core.data.repository.FakeEqualizerRepository
import com.smoothradio.radio.core.data.repository.FakePlaybackStateRepository
import com.smoothradio.radio.core.data.repository.FakeFirebaseRepository
import com.smoothradio.radio.core.data.repository.FakeRadioRepository
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.domain.usecase.CanShowAdUseCase
import com.smoothradio.radio.core.domain.usecase.RecordAdShownUseCase
import com.smoothradio.radio.core.domain.usecase.SyncAdSettingsUseCase
import com.smoothradio.radio.testutils.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class PlayerControlViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private lateinit var viewModel: PlayerControlViewModel
    private lateinit var fakeRadioRepository: FakeRadioRepository
    private lateinit var fakePlaybackStateRepository: FakePlaybackStateRepository
    private lateinit var fakeEqualizerRepository: FakeEqualizerRepository
    private lateinit var fakeAdSettingsRepository: FakeAdSettingsRepository
    private lateinit var fakeFirebaseRepository: FakeFirebaseRepository

    @Before
    fun setup() {
        fakeRadioRepository = FakeRadioRepository()
        fakePlaybackStateRepository = FakePlaybackStateRepository()
        fakeEqualizerRepository = FakeEqualizerRepository()
        fakeAdSettingsRepository = FakeAdSettingsRepository()
        fakeFirebaseRepository = FakeFirebaseRepository()

        val canShowAdUseCase = CanShowAdUseCase(fakeAdSettingsRepository)
        val recordAdShownUseCase = RecordAdShownUseCase(fakeAdSettingsRepository)
        val syncAdSettingsUseCase =
            SyncAdSettingsUseCase(fakeAdSettingsRepository, fakeFirebaseRepository)

        viewModel = PlayerControlViewModel(
            fakeRadioRepository,
            fakePlaybackStateRepository,
            fakeEqualizerRepository,
            canShowAdUseCase,
            recordAdShownUseCase,
            syncAdSettingsUseCase
        )
    }

    @Test
    fun requestPlayStation_shouldEmitCommandAndSaveId() = runTest(dispatcherRule.dispatcher) {
        fakeRadioRepository.clearAllStations()

        val station = RadioStation(
            id = 1,
            stationName = "Test",
            frequency = "1.1",
            location = "City",
            streamLink = "url",
            isPlaying = false,
            isFavorite = false,
            orderIndex = 0
        )
        fakeRadioRepository.insertStations(listOf(station))

        val commands = mutableListOf<PlayCommand>()
        backgroundScope.launch { viewModel.playCommand.toList(commands) }

        viewModel.requestPlayStation(station)
        advanceUntilIdle()  // Process all pending coroutines

        assertThat(commands).containsExactly(PlayCommand.PlayStation(station))
        assertThat(viewModel.playingStation.value?.id).isEqualTo(1)

        val repoStation = fakeRadioRepository.playingStation.first { it != null }
        assertThat(repoStation?.id).isEqualTo(1)
    }

    @Test
    fun requestNextStation_shouldCalculateNextAndPlay() = runTest(dispatcherRule.dispatcher) {
        fakeRadioRepository.clearAllStations()

        val stations = listOf(
            RadioStation(1, "S1", "", "", "u1", false, false, 0),
            RadioStation(2, "S2", "", "", "u2", false, false, 1),
            RadioStation(3, "S3", "", "", "u3", false, false, 2)
        )
        fakeRadioRepository.insertStations(stations)

        fakeRadioRepository.setPlayingStation(1)
        advanceUntilIdle()

        assertThat(viewModel.playingStation.value?.id).isEqualTo(1)

        viewModel.requestNextStation()
        
        val command = viewModel.playCommand.first()
        assertThat((command as PlayCommand.PlayStation).station.id).isEqualTo(2)

        advanceUntilIdle()
        assertThat(viewModel.playingStation.value?.id).isEqualTo(2)
    }

    @Test
    fun requestNextStation_atEnd_shouldWrapToFirst() = runTest(dispatcherRule.dispatcher) {
        fakeRadioRepository.clearAllStations()

        val stations = listOf(
            RadioStation(1, "S1", "", "", "u1", false, false, 0),
            RadioStation(2, "S2", "", "", "u2", false, false, 1)
        )
        fakeRadioRepository.insertStations(stations)
        fakeRadioRepository.setPlayingStation(2)
        
        assertThat(viewModel.playingStation.value?.id).isEqualTo(2)

        viewModel.requestNextStation()
        advanceUntilIdle()

        assertThat(viewModel.playingStation.value?.id).isEqualTo(1)
    }

    @Test
    fun requestPreviousStation_shouldCalculatePrevAndPlay() = runTest(dispatcherRule.dispatcher) {
        fakeRadioRepository.clearAllStations()

        val stations = listOf(
            RadioStation(1, "S1", "", "", "u1", false, false, 0),
            RadioStation(2, "S2", "", "", "u2", false, false, 1)
        )
        fakeRadioRepository.insertStations(stations)
        fakeRadioRepository.setPlayingStation(2)
        
        assertThat(viewModel.playingStation.value?.id).isEqualTo(2)

        viewModel.requestPreviousStation()
        advanceUntilIdle()

        assertThat(viewModel.playingStation.value?.id).isEqualTo(1)
    }

    @Test
    fun requestPreviousStation_atStart_shouldWrapToEnd() = runTest(dispatcherRule.dispatcher) {
        fakeRadioRepository.clearAllStations()

        val stations = listOf(
            RadioStation(1, "S1", "", "", "u1", false, false, 0),
            RadioStation(2, "S2", "", "", "u2", false, false, 1)
        )
        fakeRadioRepository.insertStations(stations)
        fakeRadioRepository.setPlayingStation(1)
        
        assertThat(viewModel.playingStation.value?.id).isEqualTo(1)

        viewModel.requestPreviousStation()
        advanceUntilIdle()

        assertThat(viewModel.playingStation.value?.id).isEqualTo(2)
    }

    @Test
    fun setEqualizerBand_shouldCallRepositoryAndEmitCommand() = runTest(dispatcherRule.dispatcher) {
        val commands = mutableListOf<PlayCommand>()
        backgroundScope.launch { viewModel.playCommand.toList(commands) }

        val band = 0
        val level = 500.toShort()
        viewModel.setEqualizerBand(band, level)
        advanceUntilIdle()  // Process all pending coroutines

        assertThat(fakeEqualizerRepository.getBandLevel(band)).isEqualTo(level)
        assertThat(commands).containsExactly(PlayCommand.SetEqBand(band, level))
    }

    @Test
    fun playbackState_shouldBeBuffering_whenStationIsChanging() = runTest(dispatcherRule.dispatcher) {
        val station = RadioStation(1, "S1", "", "", "u1", false, false, 0)
        fakeRadioRepository.insertStations(listOf(station))

        // Initial state
        fakePlaybackStateRepository.updateState(StreamStates.PLAYING)
        advanceUntilIdle()
        assertThat(viewModel.playbackState.value).isEqualTo(StreamStates.PLAYING)

        // Request new station
        viewModel.requestPlayStation(station)

        // VERIFY RESET: repo state should be reset to PREPARING immediately
        assertThat(fakePlaybackStateRepository.playbackState.value).isEqualTo(StreamStates.PREPARING)
        
        // Simulating Service reset: Repo moves to IDLE
        fakePlaybackStateRepository.updateState(StreamStates.IDLE)
        advanceUntilIdle()

        // VM should STILL report BUFFERING even if repo is IDLE (because guard is active)
        assertThat(viewModel.playbackState.value).isEqualTo(StreamStates.BUFFERING)
        assertThat(viewModel.isStationChanging.value).isTrue()

        // Simulate DB sync - guard should NOT clear yet in the new logic
        fakeRadioRepository.setPlayingStation(1)
        advanceUntilIdle()
        assertThat(viewModel.isStationChanging.value).isTrue()

        // Finally simulate player ready
        fakePlaybackStateRepository.updateStationId(1)
        fakePlaybackStateRepository.updateState(StreamStates.PLAYING)
        advanceUntilIdle()

        assertThat(viewModel.isStationChanging.value).isFalse()
        assertThat(viewModel.playbackState.value).isEqualTo(StreamStates.PLAYING)
    }

    @Test
    fun requestRefresh_shouldResetStateAndSetGuard() = runTest(dispatcherRule.dispatcher) {
        // Setup a playing station first so the ViewModel has a 'targetStation'
        val station = RadioStation(1, "Test", "1.1", "City", "url", true, false, 0)
        fakeRadioRepository.insertStations(listOf(station))
        viewModel.requestPlayStation(station)
        fakePlaybackStateRepository.updateStationId(1)
        fakePlaybackStateRepository.updateState(StreamStates.PLAYING)
        advanceUntilIdle()
        
        // Ensure guard is cleared after initial play
        assertThat(viewModel.isStationChanging.value).isFalse()
        
        viewModel.requestRefresh()
        
        assertThat(fakePlaybackStateRepository.playbackState.value).isEqualTo(StreamStates.PREPARING)
        assertThat(fakePlaybackStateRepository.loadingProgress.value).isEqualTo(0f)
        assertThat(viewModel.isStationChanging.value).isTrue()
    }

    @Test
    fun togglePlayPause_shouldEmitCommandAndClearChangingGuard() = runTest(dispatcherRule.dispatcher) {
        val commands = mutableListOf<PlayCommand>()
        backgroundScope.launch { viewModel.playCommand.toList(commands) }

        // 1. Enter changing state
        val station = RadioStation(1, "S1", "", "", "u1", false, false, 0)
        fakeRadioRepository.insertStations(listOf(station))
        viewModel.requestPlayStation(station)
        advanceUntilIdle()
        assertThat(viewModel.isStationChanging.value).isTrue()

        // 2. Toggle while changing - should clear guard
        viewModel.togglePlayPause()
        advanceUntilIdle()

        assertThat(commands).contains(PlayCommand.TogglePlayPause)
        assertThat(viewModel.isStationChanging.value).isFalse()
    }

    @Test
    fun requestNextStation_shouldSetChangingGuard() = runTest(dispatcherRule.dispatcher) {
        val stations = listOf(
            RadioStation(1, "S1", "", "", "u1", false, false, 0),
            RadioStation(2, "S2", "", "", "u2", false, false, 1)
        )
        fakeRadioRepository.insertStations(stations)
        advanceUntilIdle()

        viewModel.requestNextStation()
        advanceUntilIdle()

        assertThat(viewModel.isStationChanging.value).isTrue()
        assertThat(fakePlaybackStateRepository.playbackState.value).isEqualTo(StreamStates.PREPARING)
    }

    @Test
    fun requestPreviousStation_shouldSetChangingGuard() = runTest(dispatcherRule.dispatcher) {
        val stations = listOf(
            RadioStation(1, "S1", "", "", "u1", false, false, 0),
            RadioStation(2, "S2", "", "", "u2", false, false, 1)
        )
        fakeRadioRepository.insertStations(stations)
        advanceUntilIdle()

        viewModel.requestPreviousStation()
        advanceUntilIdle()

        assertThat(viewModel.isStationChanging.value).isTrue()
        assertThat(fakePlaybackStateRepository.playbackState.value).isEqualTo(StreamStates.PREPARING)
    }

    @Test
    fun requestPlayStation_sameStation_shouldToggleAndClearGuard() = runTest(dispatcherRule.dispatcher) {
        val station = RadioStation(1, "S1", "", "", "u1", false, false, 0)
        fakeRadioRepository.insertStations(listOf(station))
        
        val commands = mutableListOf<PlayCommand>()
        backgroundScope.launch { viewModel.playCommand.toList(commands) }

        // Initial play
        viewModel.requestPlayStation(station)
        fakePlaybackStateRepository.updateStationId(1)
        advanceUntilIdle()
        assertThat(viewModel.isStationChanging.value).isFalse()
        assertThat(commands).contains(PlayCommand.PlayStation(station))
        commands.clear() // Clear the initial PlayStation command

        // Request same station again
        viewModel.requestPlayStation(station)
        advanceUntilIdle()

        assertThat(commands).containsExactly(PlayCommand.TogglePlayPause)
        assertThat(viewModel.isStationChanging.value).isFalse()
    }

    @Test
    fun showToast_shouldEmitToastType() = runTest(dispatcherRule.dispatcher) {
        val toasts = mutableListOf<ToastType>()
        val job = backgroundScope.launch {
            viewModel.toastMessage.collect { toasts.add(it) }
        }

        val expectedToast = ToastType.Success("Connected")
        viewModel.showToast(expectedToast)
        advanceUntilIdle()

        assertThat(toasts).containsExactly(expectedToast)
        job.cancel()
    }
}
