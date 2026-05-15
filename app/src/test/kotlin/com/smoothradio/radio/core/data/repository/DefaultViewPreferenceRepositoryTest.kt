package com.smoothradio.radio.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.domain.repository.ViewPreferenceRepository
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
class DefaultViewPreferenceRepositoryTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher + Job())

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: ViewPreferenceRepository

    @Before
    fun setup() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tmpFolder.newFile("view_preferences.preferences_pb") }
        )
        repository = DefaultViewPreferenceRepository(dataStore)
    }

    @Test
    fun getIsGridView_shouldReturnFalseByDefault() = runTest {
        assertThat(repository.getIsGridView()).isFalse()
    }

    @Test
    fun saveIsGridView_shouldPersistValue() = runTest {
        repository.saveIsGridView(true)
        assertThat(repository.getIsGridView()).isTrue()

        repository.saveIsGridView(false)
        assertThat(repository.getIsGridView()).isFalse()
    }

    @Test
    fun getIsGridViewFlow_shouldEmitUpdates() = runTest {
        repository.saveIsGridView(true)
        assertThat(repository.getIsGridViewFlow().first()).isTrue()

        repository.saveIsGridView(false)
        assertThat(repository.getIsGridViewFlow().first()).isFalse()
    }
}
