package com.smoothradio.radio.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.data.repository.FakeFirebaseRepository
import com.smoothradio.radio.core.data.repository.FakeRadioRepository
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.domain.repository.FirebaseRepository
import com.smoothradio.radio.core.domain.repository.RadioRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class ProcessRemoteLinksUseCaseTest {

    private lateinit var repository: RadioRepository
    private lateinit var firebaseRepository: FirebaseRepository
    private lateinit var useCase: ProcessRemoteLinksUseCase

    @Before
    fun setup() {
        repository = FakeRadioRepository()
        firebaseRepository = FakeFirebaseRepository()
        useCase = ProcessRemoteLinksUseCase(repository, firebaseRepository)
    }

    @Test
    fun invoke_success_initialImport_shouldSetFirstStationAsPlaying() = runTest {
        backgroundScope.launch { useCase.invoke() }

        // Wait for repository to have the full list from Firebase (RadioStationsHelper adds 214)
        val stations = repository.allStations.filter { it.size > 10 }.first()
        val playing = repository.playingStation.filter { it != null }.first()

        assertThat(stations).isNotEmpty()
        assertThat(playing?.id).isEqualTo(stations.first().id)
        assertThat(playing?.isPlaying).isTrue()
    }

    @Test
    fun invoke_success_preserveFavoritesAndPlaying() = runTest {
        // Clear initial dummy stations
        repository.clearAllStations()

        // Prepare local stations
        val localStations = listOf(
            RadioStation(0, "HOPE FM", "93.3", "NAIROBI", "local-url", false, true, 0),
            RadioStation(1, "SOUNDCITY RADIO", "88.5", "NAIROBI", "local-url", true, false, 1)
        )
        repository.insertStations(localStations)
        repository.setPlayingStation(1)

        backgroundScope.launch { useCase.invoke() }

        // Wait for update to happen (HOPE FM's streamLink should change from "local-url" to remote)
        val stations = repository.allStations
            .filter { list -> list.any { it.id == 0 && it.streamLink == "https://a5.asurahosting.com:7530/radio.mp3" } }
            .first()

        val hopeFm = stations.find { it.id == 0 }
        val soundCity = stations.find { it.id == 1 }

        assertThat(hopeFm?.isFavorite).isTrue()
        assertThat(soundCity?.isPlaying).isTrue()
        // Verify URL was updated from remote
        assertThat(hopeFm?.streamLink).isEqualTo("https://a5.asurahosting.com:7530/radio.mp3")
    }
}
