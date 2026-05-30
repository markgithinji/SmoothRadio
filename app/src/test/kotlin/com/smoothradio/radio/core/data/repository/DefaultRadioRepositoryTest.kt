package com.smoothradio.radio.core.data.repository

import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.data.local.FakeRadioStationDao
import com.smoothradio.radio.core.data.mapper.toEntity
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.domain.repository.RadioRepository
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
    private lateinit var repository: RadioRepository

    @Before
    fun setup() {
        dao = FakeRadioStationDao()
        repository = DefaultRadioRepository(dao)
    }

    @Test
    fun getAllStationsFlow_shouldEmitStationsFromDao() = runTest {
        val stations = listOf(
            RadioStation(1, "Station One", "99.1 FM", "CityA", "url1", false, false, 0),
            RadioStation(2, "Station Two", "100.2 FM", "CityB", "url2", false, true, 1)
        )
        dao.insertStations(stations.map { it.toEntity() })

        val emitted = repository.allStations.first()

        assertThat(emitted).isEqualTo(stations)
    }

    @Test
    fun getFavoriteStationsFlow_shouldEmitFavoritesFromDao() = runTest {
        val stations = listOf(
            RadioStation(1, "Station One", "99.1 FM", "CityA", "url1", false, false, 0),
            RadioStation(2, "Station Two", "100.2 FM", "CityB", "url2", false, true, 1)
        )
        dao.insertStations(stations.map { it.toEntity() })

        val emitted = repository.favoriteStations.first()

        assertThat(emitted).containsExactly(stations[1])
    }

    @Test
    fun getPlayingStationFlow_shouldEmitCurrentlyPlayingStation() = runTest {
        val stations = listOf(
            RadioStation(1, "Station One", "99.1 FM", "CityA", "url1", true, false, 0),
            RadioStation(2, "Station Two", "100.2 FM", "CityB", "url2", false, true, 1)
        )
        dao.insertStations(stations.map { it.toEntity() })
        dao.updatePlayingStation(1)

        val emitted = repository.playingStation.first()

        assertThat(emitted).isEqualTo(stations[0].copy(isPlaying = true))
    }

    @Test
    fun setPlayingStation_shouldClearPreviousAndUpdatePlayingStation() = runTest {
        val stations = listOf(
            RadioStation(1, "Station One", "99.1 FM", "CityA", "url1", true, false, 0),
            RadioStation(5, "Station Five", "103.5 FM", "CityE", "url5", false, false, 1)
        )
        dao.insertStations(stations.map { it.toEntity() })

        repository.setPlayingStation(5)

        val updated = repository.playingStation.first()

        assertThat(updated?.id).isEqualTo(5)
        assertThat(updated?.isPlaying).isTrue()
    }

    @Test
    fun insertStations_shouldCallDaoInsert() = runTest {
        val stations = listOf(
            RadioStation(1, "Station One", "99.1 FM", "CityA", "url1", false, false, 0)
        )

        repository.insertStations(stations)

        val stored = repository.allStations.first()
        assertThat(stored).containsExactlyElementsIn(stations)
    }

    @Test
    fun updateFavoriteStatus_shouldCallDaoUpdate() = runTest {
        val station = RadioStation(3, "Station Three", "102.2 FM", "CityC", "url3", false, false, 0)
        dao.insertStations(listOf(station.toEntity()))

        repository.updateFavoriteStatus(3, true)

        val favorites = repository.favoriteStations.first()
        assertThat(favorites).containsExactly(station.copy(isFavorite = true))
    }

    @Test
    fun clearAllStations_shouldCallDaoClearAll() = runTest {
        val stations = listOf(
            RadioStation(1, "Station One", "99.1 FM", "CityA", "url1", false, false, 0)
        )
        dao.insertStations(stations.map { it.toEntity() })

        repository.clearAllStations()

        val stored = repository.allStations.first()
        assertThat(stored).isEmpty()
    }

    @Test
    fun deleteStations_shouldCallDaoDelete() = runTest {
        val stations = listOf(
            RadioStation(1, "Station One", "99.1 FM", "CityA", "url1", false, false, 0),
            RadioStation(2, "Station Two", "100.2 FM", "CityB", "url2", false, false, 1)
        )
        dao.insertStations(stations.map { it.toEntity() })

        repository.deleteStations(listOf(stations[0]))

        val stored = repository.allStations.first()
        assertThat(stored).containsExactly(stations[1])
    }
}
