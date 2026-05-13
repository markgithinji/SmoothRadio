package com.smoothradio.radio.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.domain.repository.EqualizerRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@ExperimentalCoroutinesApi
@RunWith(JUnit4::class)
class DefaultEqualizerRepositoryTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher + Job())

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: EqualizerRepository

    @Before
    fun setup() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tmpFolder.newFile("equalizer.preferences_pb") }
        )
        repository = DefaultEqualizerRepository(dataStore)
    }

    @Test
    fun getBandLevel_shouldReturnZeroByDefault() = runTest {
        assertThat(repository.getBandLevel(0)).isEqualTo(0.toShort())
    }

    @Test
    fun saveBandLevel_shouldPersistLevel() = runTest {
        val band = 2
        val level: Short = 500
        repository.saveBandLevel(band, level)

        assertThat(repository.getBandLevel(band)).isEqualTo(level)
    }

    @Test
    fun getBandLevelsFlow_shouldEmitAllBandLevels() = runTest {
        repository.saveBandLevel(0, 100)
        repository.saveBandLevel(4, 400)

        val levels = repository.getBandLevelsFlow().first()

        assertThat(levels[0]).isEqualTo(100.toShort())
        assertThat(levels[1]).isEqualTo(0.toShort())
        assertThat(levels[2]).isEqualTo(0.toShort())
        assertThat(levels[3]).isEqualTo(0.toShort())
        assertThat(levels[4]).isEqualTo(400.toShort())
        assertThat(levels.size).isEqualTo(5)
    }
}
