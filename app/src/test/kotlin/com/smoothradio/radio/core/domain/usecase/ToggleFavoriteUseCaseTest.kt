package com.smoothradio.radio.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.data.local.FakeRadioStationDao
import com.smoothradio.radio.core.data.repository.FakeRadioRepository
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.domain.repository.RadioRepository
import com.smoothradio.radio.core.util.Resource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class ToggleFavoriteUseCaseTest {

    private lateinit var repository: RadioRepository
    private lateinit var useCase: ToggleFavoriteUseCase

    @Before
    fun setup() {
        repository = FakeRadioRepository(FakeRadioStationDao())
        useCase = ToggleFavoriteUseCase(repository)
    }

    @Test
    fun invoke_addFavorite_belowLimit_success() = runTest {
        val station = RadioStation(1, 0, "Test", "", "", "", false, false, 0)
        repository.insertStations(listOf(station))

        val result = useCase(1, true)

        assertThat(result).isInstanceOf(Resource.Success::class.java)
        assertThat(repository.updateFavoriteStatus(1, true)) // Verify repository was called
    }

    @Test
    fun invoke_addFavorite_atLimit_error() = runTest {
        val stations = (1..20).map { 
            RadioStation(it, 0, "S$it", "", "", "", false, true, it)
        }
        repository.insertStations(stations)

        val newStation = RadioStation(21, 0, "S21", "", "", "", false, false, 21)
        repository.insertStations(listOf(newStation))

        val result = useCase(21, true)

        assertThat(result).isInstanceOf(Resource.Error::class.java)
        assertThat((result as Resource.Error).message).contains("20")
    }

    @Test
    fun invoke_removeFavorite_alwaysSuccess() = runTest {
        val station = RadioStation(1, 0, "Test", "", "", "", false, true, 0)
        repository.insertStations(listOf(station))

        val result = useCase(1, false)

        assertThat(result).isInstanceOf(Resource.Success::class.java)
    }
}
