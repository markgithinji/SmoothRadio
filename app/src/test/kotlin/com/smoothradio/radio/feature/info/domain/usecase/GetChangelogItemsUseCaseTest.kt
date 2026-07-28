package com.smoothradio.radio.feature.info.domain.usecase

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GetChangelogItemsUseCaseTest {

    @Test
    fun invoke_returnsListOfChangelogItems() {
        val useCase = GetChangelogItemsUseCase()
        val items = useCase()
        
        assertThat(items).isNotEmpty()
        assertThat(items[0].title).isNotEmpty()
        assertThat(items[0].description).isNotEmpty()
    }
}
