package com.smoothradio.radio.feature.changelog.ui

import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.BuildConfig
import com.smoothradio.radio.core.data.repository.FakeViewPreferenceRepository
import com.smoothradio.radio.testutils.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class ChangelogViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: ChangelogViewModel
    private lateinit var fakeRepository: FakeViewPreferenceRepository

    @Before
    fun setup() {
        fakeRepository = FakeViewPreferenceRepository()
    }

    @Test
    fun init_whenCurrentVersionIsGreaterThanLastShown_shouldShowChangelogAfterDelay() = runTest {
        // Given last shown version is 0 (default) and current version is > 0
        viewModel = ChangelogViewModel(fakeRepository)

        // Then initially it should be false
        assertThat(viewModel.shouldShowChangelog.value).isFalse()

        // When delay passes
        advanceTimeBy(2001)

        // Then it should be true
        assertThat(viewModel.shouldShowChangelog.value).isTrue()
    }

    @Test
    fun init_whenCurrentVersionIsSameAsLastShown_shouldNotShowChangelog() = runTest {
        // Given last shown version is same as current version
        fakeRepository.saveLastShownVersion(BuildConfig.VERSION_CODE)
        
        viewModel = ChangelogViewModel(fakeRepository)
        advanceTimeBy(2001)

        // Then it should still be false
        assertThat(viewModel.shouldShowChangelog.value).isFalse()
    }

    @Test
    fun onChangelogDismissed_shouldSaveCurrentVersionAndHideChangelog() = runTest {
        // Given changelog is shown
        viewModel = ChangelogViewModel(fakeRepository)
        advanceTimeBy(2001)
        assertThat(viewModel.shouldShowChangelog.value).isTrue()

        // When dismissed
        viewModel.onChangelogDismissed()
        advanceUntilIdle()

        // Then it should be hidden
        assertThat(viewModel.shouldShowChangelog.value).isFalse()
        // And version should be saved
        assertThat(fakeRepository.getLastShownVersion()).isEqualTo(BuildConfig.VERSION_CODE)
    }

    @Test
    fun showChangelog_shouldForceShowChangelog() = runTest {
        // Given changelog would not be shown normally (already shown)
        fakeRepository.saveLastShownVersion(BuildConfig.VERSION_CODE)
        viewModel = ChangelogViewModel(fakeRepository)
        advanceTimeBy(2001)
        assertThat(viewModel.shouldShowChangelog.value).isFalse()

        // When showChangelog is called
        viewModel.showChangelog()

        // Then it should be true
        assertThat(viewModel.shouldShowChangelog.value).isTrue()
    }
}
