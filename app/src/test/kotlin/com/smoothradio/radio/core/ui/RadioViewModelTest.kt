package com.smoothradio.radio.core.ui

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.data.local.FakeRadioStationDao
import com.smoothradio.radio.core.data.local.RadioStationDao
import com.smoothradio.radio.core.data.repository.FakeRadioLinkRepository
import com.smoothradio.radio.core.data.repository.FakeRadioRepository
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.domain.repository.RadioRepository
import com.smoothradio.radio.core.domain.usecase.ProcessRemoteLinksUseCase
import com.smoothradio.radio.core.domain.usecase.ToggleFavoriteUseCase
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

@ExperimentalCoroutinesApi
class RadioViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: RadioViewModel
    private lateinit var fakeRadioRepository: RadioRepository
    private lateinit var fakeRadioLinkRepository: FakeRadioLinkRepository
    private lateinit var remoteLinksUseCase: ProcessRemoteLinksUseCase
    private lateinit var fakeRadioStationDao: RadioStationDao
    private lateinit var toggleFavoriteUseCase: ToggleFavoriteUseCase

    private val application: Application = mock()

    @Before
    fun setup() {
        fakeRadioStationDao = FakeRadioStationDao()
        fakeRadioRepository = FakeRadioRepository(fakeRadioStationDao)
        fakeRadioLinkRepository = FakeRadioLinkRepository()
        remoteLinksUseCase =
            ProcessRemoteLinksUseCase(fakeRadioRepository, fakeRadioLinkRepository)
        toggleFavoriteUseCase = ToggleFavoriteUseCase(fakeRadioRepository)

        viewModel = RadioViewModel(
            application,
            fakeRadioLinkRepository,
            fakeRadioRepository,
            remoteLinksUseCase,
            toggleFavoriteUseCase
        )
    }

    @Test
    fun setCurrentPage_shouldUpdateStateFlow() = runTest {
        viewModel.setCurrentPage(2)
        advanceUntilIdle()

        assertThat(viewModel.currentPage.value).isEqualTo(2)
    }

    @Test
    fun setSelectedStation_shouldEmitInSharedFlow() = runTest {
        val station = RadioStation(1, 0, "Station One", "99.1 FM", "CityA", "url", false, false,0)

        val emissions = mutableListOf<RadioStation?>()
        val job = launch { viewModel.selectedStation.toList(emissions) }

        viewModel.setSelectedStation(station)
        advanceUntilIdle()

        assertThat(emissions).contains(station)
        job.cancel()
    }

    @Test
    fun savePlayingStation_shouldUpdatePlayingStationFlow() = runTest {
        val stations = listOf(
            RadioStation(5, 0, "Station Five", "101.1 FM", "CityX", "url", false, false,0)
        )
        fakeRadioRepository.insertStations(stations)

        viewModel.savePlayingStationId(5)
        advanceUntilIdle()

        val playingStation = fakeRadioRepository.playingStation.first()
        assertThat(playingStation?.id).isEqualTo(5)
    }

    @Test
    fun insertStations_shouldUpdateAllStationsFlow() = runTest {
        val stations = listOf(
            RadioStation(2, 0, "Station Two", "100.2 FM", "CityB", "url2", false, true,0)
        )

        viewModel.insertStations(stations)
        advanceUntilIdle()

        val allStations = fakeRadioRepository.allStations.first()
        assertThat(allStations).containsExactlyElementsIn(stations)
    }

    @Test
    fun toggleFavorite_shouldEmitSuccessAndUpdateFavorites() = runTest {
        val station = RadioStation(1, 0, "One", "99.1", "City", "url", false, false, 0)
        fakeRadioRepository.insertStations(listOf(station))

        val results = mutableListOf<Boolean>()
        val job = launch { viewModel.favoriteToggleResult.toList(results) }

        viewModel.toggleFavorite(1, true)
        advanceUntilIdle()

        val updatedFavorites = fakeRadioRepository.favoriteStations.first()
        assertThat(updatedFavorites.map { it.id }).containsExactly(1)
        assertThat(results).containsExactly(true)

        job.cancel()
    }

    @Test
    fun onCleared_shouldCallRadioLinkRepositoryClear() {
        viewModel.onCleared()
        assertThat(fakeRadioLinkRepository.clearCalled).isTrue()
    }
}

