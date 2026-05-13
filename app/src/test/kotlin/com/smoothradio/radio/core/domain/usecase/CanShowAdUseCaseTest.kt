package com.smoothradio.radio.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.data.repository.FakeAdSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class CanShowAdUseCaseTest {

    private lateinit var adSettingsRepository: FakeAdSettingsRepository
    private lateinit var useCase: CanShowAdUseCase

    @Before
    fun setup() {
        adSettingsRepository = FakeAdSettingsRepository()
        useCase = CanShowAdUseCase(adSettingsRepository)
    }

    @Test
    fun invoke_noAdsShownYet_returnsTrue() = runTest {
        adSettingsRepository.updateAdSettings(intervalMinutes = 5, maxAdsPerHour = 3)
        
        val result = useCase()

        assertThat(result).isTrue()
    }

    @Test
    fun invoke_tooSoonAfterLastAd_returnsFalse() = runTest {
        val now = System.currentTimeMillis()
        val hour = now / (1000 * 60 * 60)
        adSettingsRepository.updateAdSettings(intervalMinutes = 5, maxAdsPerHour = 3)
        adSettingsRepository.updateAdDataWithCount(now - (2 * 60 * 1000), hour, 1) // 2 mins ago
        
        val result = useCase()

        assertThat(result).isFalse()
    }

    @Test
    fun invoke_enoughTimePassedButCountReached_returnsFalse() = runTest {
        val now = System.currentTimeMillis()
        val hour = now / (1000 * 60 * 60)
        adSettingsRepository.updateAdSettings(intervalMinutes = 5, maxAdsPerHour = 3)
        adSettingsRepository.updateAdDataWithCount(now - (10 * 60 * 1000), hour, 3) // 10 mins ago, but 3 ads shown
        
        val result = useCase()

        assertThat(result).isFalse()
    }

    @Test
    fun invoke_newHourStarted_resetsEffectiveCount_returnsTrue() = runTest {
        val now = System.currentTimeMillis()
        val lastHour = (now / (1000 * 60 * 60)) - 1
        adSettingsRepository.updateAdSettings(intervalMinutes = 5, maxAdsPerHour = 3)
        // 3 ads shown in the LAST hour, 10 mins ago
        adSettingsRepository.updateAdDataWithCount(now - (10 * 60 * 1000), lastHour, 3) 
        
        val result = useCase()

        assertThat(result).isTrue()
    }
}
