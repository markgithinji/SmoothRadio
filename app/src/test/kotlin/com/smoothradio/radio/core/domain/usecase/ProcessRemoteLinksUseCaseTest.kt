package com.smoothradio.radio.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.data.local.FakeRadioStationDao
import com.smoothradio.radio.core.data.repository.FakeFirebaseRepository
import com.smoothradio.radio.core.data.repository.FakeRadioRepository
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.domain.repository.FirebaseRepository
import com.smoothradio.radio.core.domain.repository.RadioRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class ProcessRemoteLinksUseCaseTest {

    private lateinit var repository: RadioRepository
    private lateinit var firebaseRepository: FirebaseRepository
    private lateinit var useCase: ProcessRemoteLinksUseCase
    private lateinit var fakeRadioStationDao: FakeRadioStationDao

    @Before
    fun setup() {
        fakeRadioStationDao = FakeRadioStationDao()
        repository = FakeRadioRepository(fakeRadioStationDao)
        firebaseRepository = FakeFirebaseRepository()
        useCase = ProcessRemoteLinksUseCase(repository, firebaseRepository)
    }

    @Test
    fun invoke_success_initialImport_shouldSetFirstStationAsPlaying() = runTest {
        useCase.invoke()

        val stations = repository.allStations.first()
        val playing = repository.playingStation.first()

        assertThat(stations).isNotEmpty()
        assertThat(playing?.id).isEqualTo(stations.first().id)
        assertThat(playing?.isPlaying).isTrue()
    }

    @Test
    fun invoke_success_preserveFavoritesAndPlaying() = runTest {
        // Prepare local stations
        val localStations = listOf(
            RadioStation(0, "HOPE FM", "93.3", "NAIROBI", "local-url", false, true, 0),
            RadioStation(1, "SOUNDCITY RADIO", "88.5", "NAIROBI", "local-url", true, false, 1)
        )
        repository.insertStations(localStations)
        repository.setPlayingStation(1)

        useCase.invoke()

        val stations = repository.allStations.first()
        val hopeFm = stations.find { it.id == 0 }
        val soundCity = stations.find { it.id == 1 }

        assertThat(hopeFm?.isFavorite).isTrue()
        assertThat(soundCity?.isPlaying).isTrue()
        // Verify URL was updated from remote
        assertThat(hopeFm?.streamLink).isEqualTo("https://stream0.com")
    }
}
