package com.smoothradio.radio.core.ui

import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.data.local.FakeRadioStationDao
import com.smoothradio.radio.core.data.local.RadioStationDao
import com.smoothradio.radio.core.data.repository.FakeFirebaseRepository
import com.smoothradio.radio.core.data.repository.FakeRadioRepository
import com.smoothradio.radio.core.data.repository.FakeViewPreferenceRepository
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.domain.repository.RadioRepository
import com.smoothradio.radio.core.domain.usecase.ProcessRemoteLinksUseCase
import com.smoothradio.radio.core.domain.usecase.ToggleFavoriteUseCase
import com.smoothradio.radio.testutils.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class RadioViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private lateinit var viewModel: RadioViewModel
    private lateinit var fakeRadioRepository: RadioRepository
    private lateinit var fakeRadioLinkRepository: FakeFirebaseRepository
    private lateinit var fakeViewPreferenceRepository: FakeViewPreferenceRepository
    private lateinit var remoteLinksUseCase: ProcessRemoteLinksUseCase
    private lateinit var fakeRadioStationDao: RadioStationDao
    private lateinit var toggleFavoriteUseCase: ToggleFavoriteUseCase

    @Before
    fun setup() {
        fakeRadioStationDao = FakeRadioStationDao()
        fakeRadioRepository = FakeRadioRepository(fakeRadioStationDao)
        fakeRadioLinkRepository = FakeFirebaseRepository()
        fakeViewPreferenceRepository = FakeViewPreferenceRepository()
        remoteLinksUseCase =
            ProcessRemoteLinksUseCase(fakeRadioRepository, fakeRadioLinkRepository)
        toggleFavoriteUseCase = ToggleFavoriteUseCase(fakeRadioRepository)

        viewModel = RadioViewModel(
            fakeRadioLinkRepository,
            fakeRadioRepository,
            remoteLinksUseCase,
            toggleFavoriteUseCase,
            fakeViewPreferenceRepository
        )
    }

    @Test
    fun setSelectedTab_shouldUpdateStateFlow() = runTest(dispatcherRule.dispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.setSelectedTab(2)
        advanceUntilIdle()

        assertThat(viewModel.selectedTab.value).isEqualTo(2)
    }

    @Test
    fun updateSearchQuery_shouldUpdateUiState() = runTest(dispatcherRule.dispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        fakeRadioRepository.clearAllStations()
        advanceUntilIdle()

        val stations = listOf(
            RadioStation(
                id = 1,
                stationName = "Station One",
                frequency = "99.1 FM",
                location = "CityA",
                streamLink = "url",
                isPlaying = false,
                isFavorite = false,
                orderIndex = 0
            ),
            RadioStation(
                id = 2,
                stationName = "Other Radio",
                frequency = "100.2 FM",
                location = "CityB",
                streamLink = "url2",
                isPlaying = false,
                isFavorite = false,
                orderIndex = 1
            )
        )
        fakeRadioRepository.insertStations(stations)
        advanceUntilIdle()

        viewModel.updateSearchQuery("Other")
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertThat(uiState.searchQuery).isEqualTo("Other")
        assertThat(uiState.filteredStations).hasSize(1)
        assertThat(uiState.filteredStations[0].stationName).isEqualTo("Other Radio")
    }

    @Test
    fun setSearchActive_false_shouldClearSearchQuery() = runTest(dispatcherRule.dispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.updateSearchQuery("Test")
        viewModel.setSearchActive(false)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.searchQuery).isEmpty()
        assertThat(viewModel.uiState.value.isSearchActive).isFalse()
    }

    @Test
    fun toggleViewPreference_shouldUpdateRepositoryAndState() = runTest(dispatcherRule.dispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        // Initial state is false (list)
        assertThat(viewModel.uiState.value.isGridView).isFalse()

        viewModel.toggleViewPreference()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isGridView).isTrue()
        assertThat(fakeViewPreferenceRepository.getIsGridView()).isTrue()
    }

    @Test
    fun toggleFavorite_error_shouldEmitMessage() = runTest(dispatcherRule.dispatcher) {
        val messages = mutableListOf<String>()
        val job = backgroundScope.launch {
            viewModel.favoriteLimitExceeded.collect { messages.add(it) }
        }
        advanceUntilIdle()  // ← Let collector register

        val stations = (1000..1019).map {
            RadioStation(
                id = it, stationName = "Station $it",
                frequency = "", location = "", streamLink = "",
                isPlaying = false, isFavorite = true, orderIndex = it
            )
        }
        fakeRadioRepository.insertStations(stations)
        advanceUntilIdle()

        val favorites = fakeRadioRepository.favoriteStations.first()
        assertThat(favorites).hasSize(20)

        val newStation = RadioStation(
            id = 2000, stationName = "New",
            frequency = "", location = "", streamLink = "",
            isPlaying = false, isFavorite = false, orderIndex = 2000
        )
        fakeRadioRepository.insertStations(listOf(newStation))
        advanceUntilIdle()

        viewModel.toggleFavorite(2000, true)
        advanceUntilIdle()

        assertThat(messages).isNotEmpty()
        assertThat(messages[0]).contains("20")

        job.cancel()
    }

    @Test
    fun insertStations_shouldUpdateAllStationsFlow() = runTest(dispatcherRule.dispatcher) {
        // Collect allStations in background to keep it active
        backgroundScope.launch { viewModel.allStations.collect {} }
        advanceUntilIdle()

        // Clear any stations inserted during ViewModel initialization
        fakeRadioRepository.clearAllStations()
        advanceUntilIdle()
        
        val stations = listOf(
            RadioStation(
                id = 3000,
                stationName = "Station Three",
                frequency = "100.2 FM",
                location = "CityC",
                streamLink = "url3",
                isPlaying = false,
                isFavorite = true,
                orderIndex = 0
            )
        )

        viewModel.insertStations(stations)
        advanceUntilIdle()

        assertThat(viewModel.allStations.value).containsExactlyElementsIn(stations)
    }
}
