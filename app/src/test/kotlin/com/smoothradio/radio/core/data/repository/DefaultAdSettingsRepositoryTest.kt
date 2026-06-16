package com.smoothradio.radio.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.domain.repository.AdSettingsRepository
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
    fun adSettings_shouldReturnDefaultValues() = runTest {
        val settings = repository.getAdSettings()
        assertThat(settings.lastAdShowTime).isEqualTo(0L)
        assertThat(settings.adShowCount).isEqualTo(0L)
        assertThat(settings.lastAdHour).isEqualTo(0L)
        assertThat(settings.adShowIntervalMinutes).isEqualTo(4)
        assertThat(settings.maxAdsPerHour).isEqualTo(4)
    }

    @Test
    fun updateAdDataWithCount_shouldPersistData() = runTest {
        val time = 123456789L
        val hour = 10L
        val count = 2L

        repository.updateAdDataWithCount(time, hour, count)

        val settings = repository.getAdSettings()
        assertThat(settings.lastAdShowTime).isEqualTo(time)
        assertThat(settings.lastAdHour).isEqualTo(hour)
        assertThat(settings.adShowCount).isEqualTo(count)
    }

    @Test
    fun updateAdSettings_shouldPersistSettings() = runTest {
        val interval = 10
        val maxAds = 5

        repository.updateAdSettings(interval, maxAds)

        val settings = repository.getAdSettings()
        assertThat(settings.adShowIntervalMinutes).isEqualTo(interval)
        assertThat(settings.maxAdsPerHour).isEqualTo(maxAds)
    }

    @Test
    fun clearAll_shouldResetToDefaults() = runTest {
        repository.updateAdDataWithCount(100L, 1L, 1L)
        repository.updateAdSettings(10, 5)

        repository.clearAll()

        val settings = repository.getAdSettings()
        assertThat(settings.lastAdShowTime).isEqualTo(0L)
        assertThat(settings.adShowIntervalMinutes).isEqualTo(4) // Default value
        assertThat(settings.maxAdsPerHour).isEqualTo(4) // Default value
    }
}
