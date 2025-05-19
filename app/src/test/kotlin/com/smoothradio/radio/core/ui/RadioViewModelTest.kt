package com.smoothradio.radio.core.ui

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.data.repository.FakeRadioLinkRepository
import com.smoothradio.radio.core.data.repository.FakeRadioRepository
import com.smoothradio.radio.core.model.RadioStation
import com.smoothradio.radio.core.util.RadioStationLinksHelper
import com.smoothradio.radio.core.util.Resource
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
    private lateinit var fakeRadioRepository: FakeRadioRepository
    private lateinit var fakeRadioLinkRepository: FakeRadioLinkRepository
    private val application: Application = mock()

    @Before
    fun setup() {
        fakeRadioRepository = FakeRadioRepository()
        fakeRadioLinkRepository = FakeRadioLinkRepository()

        viewModel = RadioViewModel(application, fakeRadioLinkRepository, fakeRadioRepository)
    }

    @Test
    fun setCurrentPage_shouldUpdateStateFlow() = runTest {
        viewModel.setCurrentPage(2)
        advanceUntilIdle()

        assertThat(viewModel.currentPage.value).isEqualTo(2)
    }

    @Test
    fun setSelectedStation_shouldEmitInSharedFlow() = runTest {
        val station = RadioStation(1, 0, "Station One", "99.1 FM", "CityA", "url", false, false)

        val emissions = mutableListOf<RadioStation?>()
        val job = launch { viewModel.selectedStation.toList(emissions) }

        viewModel.setSelectedStation(station)
        advanceUntilIdle()

        assertThat(emissions).contains(station)
        job.cancel()
    }

    @Test
    fun remoteLinks_shouldEmitLinksFromRepository() = runTest {
        val result = viewModel.remoteLinks.first()
        advanceUntilIdle()

        assertThat(result).isInstanceOf(Resource.Success::class.java)
        val data = (result as Resource.Success).data
        assertThat(data).containsExactlyElementsIn(RadioStationLinksHelper.RADIO_STATIONS)
    }

    @Test
    fun savePlayingStation_shouldUpdatePlayingStationFlow() = runTest {
        val stations = listOf(
            RadioStation(5, 0, "Station Five", "101.1 FM", "CityX", "url", false, false)
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
            RadioStation(2, 0, "Station Two", "100.2 FM", "CityB", "url2", false, true)
        )

        viewModel.insertStations(stations)
        advanceUntilIdle()

        val allStations = fakeRadioRepository.allStations.first()
        assertThat(allStations).containsExactlyElementsIn(stations)
    }

    @Test
    fun updateFavoriteStatus_shouldUpdateFavoritesFlow() = runTest {
        val stations = listOf(
            RadioStation(1, 0, "One", "99.1", "City", "url", false, false),
            RadioStation(2, 0, "Two", "100.2", "City", "url", false, false)
        )
        fakeRadioRepository.insertStations(stations)

        viewModel.updateFavoriteStatus(2, true)
        advanceUntilIdle()

        val favorites = fakeRadioRepository.favoriteStations.first()
        assertThat(favorites.map { it.id }).containsExactly(2)
    }

    @Test
    fun onCleared_shouldCallRadioLinkRepositoryClear() {
        viewModel.onCleared()
        assertThat(fakeRadioLinkRepository.clearCalled).isTrue()
    }
}

