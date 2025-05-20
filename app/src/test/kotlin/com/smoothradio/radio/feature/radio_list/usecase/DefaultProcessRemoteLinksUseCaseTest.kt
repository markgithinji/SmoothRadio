package com.smoothradio.radio.feature.radio_list.usecase

import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.data.repository.FakeRadioRepository
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.util.Resource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class DefaultProcessRemoteLinksUseCaseTest {

    private lateinit var repository: FakeRadioRepository
    private lateinit var useCase: DefaultProcessRemoteLinksUseCase

    @Before
    fun setup() = runTest {
        repository = FakeRadioRepository()

        repository.insertStations(
            listOf(
                RadioStation(id = 1, 0, "Station 1", "101.1", "City", "stream1", false, false),
                RadioStation(id = 2, 0, "Station 2", "101.2", "City", "stream2", false, false)
            )
        )

        useCase = DefaultProcessRemoteLinksUseCase(repository)
    }

    @Test
    fun invoke_success_shouldInsertStations_andSetPlayingStation() = runTest {
        val safeLinks = List(250) { i -> "https://stream$i.com" } // At least index 228 exists
        useCase.invoke(Resource.Success(safeLinks))

        val stations = repository.allStations.first()
        val playing = repository.playingStation.first()

        assertThat(stations).isNotEmpty()
        assertThat(playing).isNotNull()
    }


    @Test
    fun invoke_error_shouldNotChangeStationListOrPlayingState() = runTest {
        val original = repository.allStations.first()

        useCase.invoke(Resource.Error("failed"))

        val after = repository.allStations.first()
        val playing = repository.playingStation.first()

        assertThat(after).isEqualTo(original)
        assertThat(playing).isNull()
    }

    @Test
    fun invoke_loading_shouldNotChangeStationListOrPlayingState() = runTest {
        val original = repository.allStations.first()

        useCase.invoke(Resource.Loading)

        val after = repository.allStations.first()
        val playing = repository.playingStation.first()

        assertThat(after).isEqualTo(original)
        assertThat(playing).isNull()
    }
}
