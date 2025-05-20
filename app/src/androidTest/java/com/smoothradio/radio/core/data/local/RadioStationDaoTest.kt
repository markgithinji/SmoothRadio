package com.smoothradio.radio.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.domain.model.RadioStation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class RadioStationDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: RadioStationDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        dao = database.radioStationDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    //Insert & Fetch Tests

    @Test
    fun insertStations_shouldReturnInsertedStationsOnQuery() = runTest {
        val station = dummyStation(id = 1)

        dao.insertStations(listOf(station))

        val result = dao.getAllStations().first()

        assertThat(result).containsExactly(station)
        assertThat(result.first().stationName).isEqualTo("Smooth Radio")
    }

    @Test
    fun clearAll_shouldDeleteAllStations() = runTest {
        val station = dummyStation(id = 1)

        dao.insertStations(listOf(station))
        dao.clearAll()

        val result = dao.getAllStations().first()

        assertThat(result).isEmpty()
    }

    @Test
    fun getAllStations_shouldReturnEmptyList_whenNoStationsInserted() = runTest {
        val result = dao.getAllStations().first()

        assertThat(result).isEmpty()
    }

    // Update Favorite Tests

    @Test
    fun updateFavoriteStatus_shouldReturnOnlyFavoriteStations() = runTest {
        val station = dummyStation(id = 1, isFavorite = false)

        dao.insertStations(listOf(station))
        dao.updateFavoriteStatus(1, true)

        val favorites = dao.getFavoriteStations().first()

        assertThat(favorites).containsExactly(station)
        assertThat(favorites.first().isFavorite).isTrue()
    }

    @Test
    fun updateFavoriteStatus_shouldNotFail_whenIdDoesNotExist() = runTest {
        dao.updateFavoriteStatus(999, true)

        val favorites = dao.getFavoriteStations().first()

        assertThat(favorites).isEmpty()
    }

    // Update Playing Tests

    @Test
    fun updatePlayingStation_shouldReturnCorrectPlayingStation() = runTest {
        val station = dummyStation(id = 1, isPlaying = false)

        dao.insertStations(listOf(station))
        dao.updatePlayingStation(1)

        val playingStation = dao.getPlayingStation().first()

        assertThat(playingStation).isEqualTo(station)
        assertThat(playingStation!!.isPlaying).isTrue()
    }

    @Test
    fun updatePlayingStation_shouldNotFail_whenIdDoesNotExist() = runTest {
        dao.updatePlayingStation(999)

        val playingStation = dao.getPlayingStation().first()

        assertThat(playingStation).isNull()
    }

    @Test
    fun clearPlayingState_shouldSetAllStationsToNotPlaying() = runTest {
        val station = dummyStation(id = 1, isPlaying = true)

        dao.insertStations(listOf(station))
        dao.clearPlayingState()

        val updatedStation = dao.getAllStations().first().first()

        assertThat(updatedStation.isPlaying).isFalse()
    }

    // Helpers

    private fun dummyStation(
        id: Int,
        isPlaying: Boolean = false,
        isFavorite: Boolean = false
    ) = RadioStation(
        id = id,
        logoResource = 0,
        stationName = "Smooth Radio",
        frequency = "99.9 FM",
        location = "Nairobi",
        streamLink = "https://url",
        isPlaying = isPlaying,
        isFavorite = isFavorite
    )

}
