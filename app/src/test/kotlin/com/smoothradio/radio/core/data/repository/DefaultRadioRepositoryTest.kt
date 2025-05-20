package com.smoothradio.radio.core.data.repository

import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.data.local.FakeRadioStationDao
import com.smoothradio.radio.core.domain.model.RadioStation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@ExperimentalCoroutinesApi
@RunWith(JUnit4::class)
class DefaultRadioRepositoryTest {

    private lateinit var dao: FakeRadioStationDao
    private lateinit var repository: DefaultRadioRepository

    @Before
    fun setup() {
        dao = FakeRadioStationDao()
        repository = DefaultRadioRepository(dao)
    }

    @Test
    fun getAllStationsFlow_shouldEmitStationsFromDao() = runTest {
        val stations = listOf(
            RadioStation(1, 0, "Station One", "99.1 FM", "CityA", "url1", false, false),
            RadioStation(2, 0, "Station Two", "100.2 FM", "CityB", "url2", false, true)
        )
        dao.insertStations(stations)

        val emitted = repository.allStations.first()

        assertThat(emitted).isEqualTo(stations)
    }

    @Test
    fun getFavoriteStationsFlow_shouldEmitFavoritesFromDao() = runTest {
        val stations = listOf(
            RadioStation(1, 0, "Station One", "99.1 FM", "CityA", "url1", false, false),
            RadioStation(2, 0, "Station Two", "100.2 FM", "CityB", "url2", false, true)
        )
        dao.insertStations(stations)

        val emitted = repository.favoriteStations.first()

        assertThat(emitted).containsExactly(stations[1])
    }

    @Test
    fun getPlayingStationFlow_shouldEmitCurrentlyPlayingStation() = runTest {
        val stations = listOf(
            RadioStation(1, 0, "Station One", "99.1 FM", "CityA", "url1", true, false),
            RadioStation(2, 0, "Station Two", "100.2 FM", "CityB", "url2", false, true)
        )
        dao.insertStations(stations)
        dao.updatePlayingStation(1)

        val emitted = repository.playingStation.first()

        assertThat(emitted).isEqualTo(stations[0].copy(isPlaying = true))
    }

    @Test
    fun setPlayingStation_shouldClearPreviousAndUpdatePlayingStation() = runTest {
        val stations = listOf(
            RadioStation(1, 0, "Station One", "99.1 FM", "CityA", "url1", true, false),
            RadioStation(5, 0, "Station Five", "103.5 FM", "CityE", "url5", false, false)
        )
        dao.insertStations(stations)

        repository.setPlayingStation(5)

        val updated = repository.playingStation.first()

        assertThat(updated?.id).isEqualTo(5)
        assertThat(updated?.isPlaying).isTrue()
    }

    @Test
    fun insertStations_shouldCallDaoInsert() = runTest {
        val stations = listOf(
            RadioStation(1, 0, "Station One", "99.1 FM", "CityA", "url1", false, false)
        )

        repository.insertStations(stations)

        val stored = repository.allStations.first()
        assertThat(stored).containsExactlyElementsIn(stations)
    }

    @Test
    fun updateFavoriteStatus_shouldCallDaoUpdate() = runTest {
        val station = RadioStation(3, 0, "Station Three", "102.2 FM", "CityC", "url3", false, false)
        dao.insertStations(listOf(station))

        repository.updateFavoriteStatus(3, true)

        val favorites = repository.favoriteStations.first()
        assertThat(favorites).containsExactly(station.copy(isFavorite = true))
    }
}
