package com.smoothradio.radio.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.data.repository.FakeAdSettingsRepository
import com.smoothradio.radio.core.data.repository.FakeRadioLinkRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class SyncAdSettingsUseCaseTest {

    private lateinit var adSettingsRepository: FakeAdSettingsRepository
    private lateinit var radioLinkRepository: FakeRadioLinkRepository
    private lateinit var useCase: SyncAdSettingsUseCase

    @Before
    fun setup() {
        adSettingsRepository = FakeAdSettingsRepository()
        radioLinkRepository = FakeRadioLinkRepository()
        useCase = SyncAdSettingsUseCase(adSettingsRepository, radioLinkRepository)
    }

    @Test
    fun invoke_success_updatesRepository() = runTest {
        // FakeRadioLinkRepository returns Resource.Success(RemoteAdSettings(4, 4)) by default
        
        useCase()

        assertThat(adSettingsRepository.getAdShowIntervalMinutes()).isEqualTo(4)
        assertThat(adSettingsRepository.getMaxAdsPerHour()).isEqualTo(4)
    }
}
