package com.smoothradio.radio.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.data.local.FakeRadioStationDao
import com.smoothradio.radio.core.data.repository.FakeRadioLinkRepository
import com.smoothradio.radio.core.data.repository.FakeRadioRepository
import com.smoothradio.radio.core.domain.repository.RadioLinkRepository
import com.smoothradio.radio.core.domain.repository.RadioRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ProcessRemoteLinksUseCaseTest {

    private lateinit var repository: RadioRepository
    private lateinit var radioLinkRepository: RadioLinkRepository
    private lateinit var useCase: ProcessRemoteLinksUseCase
    private lateinit var fakeRadioStationDao: FakeRadioStationDao

    @Before
    fun setup() = runTest {
        fakeRadioStationDao = FakeRadioStationDao()
        repository = FakeRadioRepository(fakeRadioStationDao)
        radioLinkRepository = FakeRadioLinkRepository()

//        repository.insertStations(
//            listOf(
//                RadioStation(id = 1, 0, "Station 1", "101.1", "City", "stream1", false, false),
//                RadioStation(id = 2, 0, "Station 2", "101.2", "City", "stream2", false, false)
//            )
//        )

        useCase = ProcessRemoteLinksUseCase(repository, radioLinkRepository)
    }

    @Test
    fun invoke_success_shouldInsertStations_andSetPlayingStation() = runTest {
        useCase.invoke()

        val stations = repository.allStations.first()
        val playing = repository.playingStation.first()

        assertThat(stations).isNotEmpty()
        assertThat(playing).isNotNull()
    }
}
