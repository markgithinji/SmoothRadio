package com.smoothradio.radio.core.ui

import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.data.local.FakeRadioStationDao
import com.smoothradio.radio.core.data.repository.DefaultPlaybackStateRepository
import com.smoothradio.radio.core.data.repository.FakeRadioRepository
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.domain.repository.EqualizerRepository
import com.smoothradio.radio.core.domain.usecase.CanShowAdUseCase
import com.smoothradio.radio.core.domain.usecase.RecordAdShownUseCase
import com.smoothradio.radio.core.domain.usecase.SyncAdSettingsUseCase
import com.smoothradio.radio.testutils.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class PlayerControlViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: PlayerControlViewModel
    private lateinit var fakeRadioRepository: FakeRadioRepository
    private lateinit var playbackStateRepository: DefaultPlaybackStateRepository
    private lateinit var equalizerRepository: EqualizerRepository
    private lateinit var canShowAdUseCase: CanShowAdUseCase
    private lateinit var recordAdShownUseCase: RecordAdShownUseCase
    private lateinit var syncAdSettingsUseCase: SyncAdSettingsUseCase

    @Before
    fun setup() {
        fakeRadioRepository = FakeRadioRepository(FakeRadioStationDao())
        playbackStateRepository = DefaultPlaybackStateRepository()
        equalizerRepository = mock()
        canShowAdUseCase = mock()
        recordAdShownUseCase = mock()
        syncAdSettingsUseCase = mock()

        // Default behavior for mocks
        whenever(equalizerRepository.getBandLevelsFlow()).thenReturn(kotlinx.coroutines.flow.flowOf(emptyMap()))

        viewModel = PlayerControlViewModel(
            fakeRadioRepository,
            playbackStateRepository,
            equalizerRepository,
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
        val job = launch { viewModel.playCommand.toList(commands) }

        viewModel.requestPlayStation(station)
        advanceUntilIdle()

        assertThat(commands).containsExactly(PlayCommand.PlayStation(station))
        assertThat(fakeRadioRepository.playingStation.first()?.id).isEqualTo(1)
        
        job.cancel()
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

        val commands = mutableListOf<PlayCommand>()
        val job = launch { viewModel.playCommand.toList(commands) }

        viewModel.requestNextStation()
        advanceUntilIdle()

        // Should play S2
        assertThat(fakeRadioRepository.playingStation.first()?.id).isEqualTo(2)
        assertThat(commands.last()).isInstanceOf(PlayCommand.PlayStation::class.java)
        assertThat((commands.last() as PlayCommand.PlayStation).station.id).isEqualTo(2)

        job.cancel()
    }

    @Test
    fun requestNextStation_atEnd_shouldWrapToFirst() = runTest {
        val stations = listOf(
            RadioStation(1, 0, "S1", "", "", "u1", false, false, 0),
            RadioStation(2, 0, "S2", "", "", "u2", false, false, 1)
        )
        fakeRadioRepository.insertStations(stations)
        fakeRadioRepository.setPlayingStation(2)
        advanceUntilIdle()

        viewModel.requestNextStation()
        advanceUntilIdle()

        assertThat(fakeRadioRepository.playingStation.first()?.id).isEqualTo(1)
    }

    @Test
    fun requestPreviousStation_shouldCalculatePrevAndPlay() = runTest {
        val stations = listOf(
            RadioStation(1, 0, "S1", "", "", "u1", false, false, 0),
            RadioStation(2, 0, "S2", "", "", "u2", false, false, 1)
        )
        fakeRadioRepository.insertStations(stations)
        fakeRadioRepository.setPlayingStation(2)
        advanceUntilIdle()

        viewModel.requestPreviousStation()
        advanceUntilIdle()

        assertThat(fakeRadioRepository.playingStation.first()?.id).isEqualTo(1)
    }

    @Test
    fun requestPreviousStation_atStart_shouldWrapToEnd() = runTest {
        val stations = listOf(
            RadioStation(1, 0, "S1", "", "", "u1", false, false, 0),
            RadioStation(2, 0, "S2", "", "", "u2", false, false, 1)
        )
        fakeRadioRepository.insertStations(stations)
        fakeRadioRepository.setPlayingStation(1)
        advanceUntilIdle()

        viewModel.requestPreviousStation()
        advanceUntilIdle()

        assertThat(fakeRadioRepository.playingStation.first()?.id).isEqualTo(2)
    }

    @Test
    fun setEqualizerBand_shouldCallRepositoryAndEmitCommand() = runTest {
        val commands = mutableListOf<PlayCommand>()
        val job = launch { viewModel.playCommand.toList(commands) }

        val band = 0
        val level = 500.toShort()
        viewModel.setEqualizerBand(band, level)
        advanceUntilIdle()

        org.mockito.kotlin.verify(equalizerRepository).saveBandLevel(band, level)
        assertThat(commands).containsExactly(PlayCommand.SetEqBand(band, level))

        job.cancel()
    }
}
