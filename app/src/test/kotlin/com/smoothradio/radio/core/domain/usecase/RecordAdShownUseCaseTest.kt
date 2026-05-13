package com.smoothradio.radio.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.data.repository.FakeAdSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class RecordAdShownUseCaseTest {

    private lateinit var adSettingsRepository: FakeAdSettingsRepository
    private lateinit var useCase: RecordAdShownUseCase

    @Before
    fun setup() {
        adSettingsRepository = FakeAdSettingsRepository()
        useCase = RecordAdShownUseCase(adSettingsRepository)
    }

    @Test
    fun invoke_firstAdInHour_setsCountToOne() = runTest {
        val now = System.currentTimeMillis()
        val lastHour = (now / (1000 * 60 * 60)) - 1
        adSettingsRepository.updateAdDataWithCount(now - (100 * 60 * 1000), lastHour, 5)
        
        useCase()

        assertThat(adSettingsRepository.getAdShowCount()).isEqualTo(1)
        assertThat(adSettingsRepository.getLastAdHour()).isEqualTo(now / (1000 * 60 * 60))
    }

    @Test
    fun invoke_subsequentAdInSameHour_incrementsCount() = runTest {
        val now = System.currentTimeMillis()
        val currentHour = now / (1000 * 60 * 60)
        adSettingsRepository.updateAdDataWithCount(now - (10 * 60 * 1000), currentHour, 2)
        
        useCase()

        assertThat(adSettingsRepository.getAdShowCount()).isEqualTo(3)
        assertThat(adSettingsRepository.getLastAdHour()).isEqualTo(currentHour)
    }
}
