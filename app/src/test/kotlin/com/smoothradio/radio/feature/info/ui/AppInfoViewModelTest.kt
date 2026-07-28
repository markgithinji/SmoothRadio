package com.smoothradio.radio.feature.info.ui

import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.BuildConfig
import com.smoothradio.radio.core.data.repository.FakeFirebaseRepository
import com.smoothradio.radio.core.data.repository.FakeViewPreferenceRepository
import com.smoothradio.radio.core.util.Resource
import com.smoothradio.radio.feature.info.domain.usecase.GetChangelogItemsUseCase
import com.smoothradio.radio.testutils.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class AppInfoViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private lateinit var viewModel: AppInfoViewModel
    private lateinit var fakeViewPreferenceRepository: FakeViewPreferenceRepository
    private lateinit var fakeFirebaseRepository: FakeFirebaseRepository
    private lateinit var getChangelogItemsUseCase: GetChangelogItemsUseCase

    @Before
    fun setup() {
        fakeViewPreferenceRepository = FakeViewPreferenceRepository()
        fakeFirebaseRepository = FakeFirebaseRepository()
        getChangelogItemsUseCase = GetChangelogItemsUseCase()
    }

    private fun createViewModel() {
        viewModel = AppInfoViewModel(
            fakeViewPreferenceRepository,
            fakeFirebaseRepository,
            getChangelogItemsUseCase
        )
    }

    @Test
    fun init_shouldShowChangelog_ifNewVersion() = runTest {
        fakeViewPreferenceRepository.saveLastShownVersion(BuildConfig.VERSION_CODE - 1)
        
        createViewModel()
        advanceUntilIdle() // Wait for checkVersion delay

        assertThat(viewModel.shouldShowChangelog.value).isTrue()
    }

    @Test
    fun init_shouldNotShowChangelog_ifSameVersion() = runTest {
        fakeViewPreferenceRepository.saveLastShownVersion(BuildConfig.VERSION_CODE)
        
        createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.shouldShowChangelog.value).isFalse()
    }

    @Test
    fun onChangelogDismissed_shouldUpdateLastShownVersionAndHide() = runTest {
        fakeViewPreferenceRepository.saveLastShownVersion(0)
        createViewModel()
        advanceUntilIdle()
        
        viewModel.onChangelogDismissed()
        
        assertThat(viewModel.shouldShowChangelog.value).isFalse()
        assertThat(fakeViewPreferenceRepository.getLastShownVersion()).isEqualTo(BuildConfig.VERSION_CODE)
    }

    @Test
    fun submitReport_shouldUpdateStateToSuccess() = runTest {
        createViewModel()
        fakeFirebaseRepository.reportResult = Resource.Success(Unit)
        
        viewModel.submitReport(
            "Audio", "Test", "1.0", "Pixel", "14"
        )
        
        assertThat(viewModel.reportState.value).isInstanceOf(Resource.Success::class.java)
        assertThat(fakeFirebaseRepository.lastReport?.get("category")).isEqualTo("Audio")
    }

    @Test
    fun submitReport_shouldUpdateStateToError() = runTest {
        createViewModel()
        fakeFirebaseRepository.reportResult = Resource.Error("Failed")
        
        viewModel.submitReport(
            "Audio", "Test", "1.0", "Pixel", "14"
        )
        
        assertThat(viewModel.reportState.value).isInstanceOf(Resource.Error::class.java)
        assertThat((viewModel.reportState.value as Resource.Error).message).isEqualTo("Failed")
    }

    @Test
    fun resetReportState_shouldClearState() = runTest {
        createViewModel()
        viewModel.submitReport("Audio", "Test", "1.0", "Pixel", "14")
        assertThat(viewModel.reportState.value).isNotNull()
        
        viewModel.resetReportState()
        assertThat(viewModel.reportState.value).isNull()
    }
}
