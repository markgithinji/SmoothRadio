package com.smoothradio.radio.core.ui

import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.data.local.FakeRadioStationDao
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
        fakeRadioRepository = FakeRadioRepository(FakeRadioStationDao())
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
    fun requestPlayStation_shouldEmitCommandAndSaveId() = runTest {
        val station = RadioStation(
            id = 1,
            logoResource = 0,
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
    fun requestNextStation_shouldCalculateNextAndPlay() = runTest {
        val stations = listOf(
            RadioStation(1, 0, "S1", "", "", "u1", false, false, 0),
            RadioStation(2, 0, "S2", "", "", "u2", false, false, 1),
            RadioStation(3, 0, "S3", "", "", "u3", false, false, 2)
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
    fun requestNextStation_atEnd_shouldWrapToFirst() = runTest {
        val stations = listOf(
            RadioStation(1, 0, "S1", "", "", "u1", false, false, 0),
            RadioStation(2, 0, "S2", "", "", "u2", false, false, 1)
        )
        fakeRadioRepository.insertStations(stations)
        fakeRadioRepository.setPlayingStation(2)
        
        assertThat(viewModel.playingStation.value?.id).isEqualTo(2)

        viewModel.requestNextStation()
        advanceUntilIdle()

        assertThat(viewModel.playingStation.value?.id).isEqualTo(1)
    }

    @Test
    fun requestPreviousStation_shouldCalculatePrevAndPlay() = runTest {
        val stations = listOf(
            RadioStation(1, 0, "S1", "", "", "u1", false, false, 0),
            RadioStation(2, 0, "S2", "", "", "u2", false, false, 1)
        )
        fakeRadioRepository.insertStations(stations)
        fakeRadioRepository.setPlayingStation(2)
        
        assertThat(viewModel.playingStation.value?.id).isEqualTo(2)

        viewModel.requestPreviousStation()
        advanceUntilIdle()

        assertThat(viewModel.playingStation.value?.id).isEqualTo(1)
    }

    @Test
    fun requestPreviousStation_atStart_shouldWrapToEnd() = runTest {
        val stations = listOf(
            RadioStation(1, 0, "S1", "", "", "u1", false, false, 0),
            RadioStation(2, 0, "S2", "", "", "u2", false, false, 1)
        )
        fakeRadioRepository.insertStations(stations)
        fakeRadioRepository.setPlayingStation(1)
        
        assertThat(viewModel.playingStation.value?.id).isEqualTo(1)

        viewModel.requestPreviousStation()
        advanceUntilIdle()

        assertThat(viewModel.playingStation.value?.id).isEqualTo(2)
    }

    @Test
    fun setEqualizerBand_shouldCallRepositoryAndEmitCommand() = runTest {
        val commands = mutableListOf<PlayCommand>()
        backgroundScope.launch { viewModel.playCommand.toList(commands) }

        val band = 0
        val level = 500.toShort()
        viewModel.setEqualizerBand(band, level)
        advanceUntilIdle()  // Process all pending coroutines

        assertThat(fakeEqualizerRepository.getBandLevel(band)).isEqualTo(level)
        assertThat(commands).containsExactly(PlayCommand.SetEqBand(band, level))
    }
}
