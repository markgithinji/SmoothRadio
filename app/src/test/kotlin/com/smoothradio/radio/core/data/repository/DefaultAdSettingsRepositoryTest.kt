package com.smoothradio.radio.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.domain.repository.AdSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
class DefaultAdSettingsRepositoryTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher + Job())

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: AdSettingsRepository

    @Before
    fun setup() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tmpFolder.newFile("ad_settings.preferences_pb") }
        )
        repository = DefaultAdSettingsRepository(dataStore)
    }

    @Test
    fun getLastAdShowTime_shouldReturnDefaultZero() = runTest {
        assertThat(repository.getLastAdShowTime()).isEqualTo(0L)
    }

    @Test
    fun updateAdDataWithCount_shouldPersistData() = runTest {
        val time = 123456789L
        val hour = 10L
        val count = 2L

        repository.updateAdDataWithCount(time, hour, count)

        assertThat(repository.getLastAdShowTime()).isEqualTo(time)
        assertThat(repository.getLastAdHour()).isEqualTo(hour)
        assertThat(repository.getAdShowCount()).isEqualTo(count)
    }

    @Test
    fun updateAdSettings_shouldPersistSettings() = runTest {
        val interval = 10
        val maxAds = 5

        repository.updateAdSettings(interval, maxAds)

        assertThat(repository.getAdShowIntervalMinutes()).isEqualTo(interval)
        assertThat(repository.getMaxAdsPerHour()).isEqualTo(maxAds)
    }

    @Test
    fun clearAll_shouldResetToDefaults() = runTest {
        repository.updateAdDataWithCount(100L, 1L, 1L)
        repository.updateAdSettings(10, 5)

        repository.clearAll()

        assertThat(repository.getLastAdShowTime()).isEqualTo(0L)
        assertThat(repository.getAdShowIntervalMinutes()).isEqualTo(4) // Default value
        assertThat(repository.getMaxAdsPerHour()).isEqualTo(4) // Default value
    }
}
