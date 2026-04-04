package com.smoothradio.radio.core.domain.di

import com.smoothradio.radio.core.domain.repository.AdSettingsRepository
import com.smoothradio.radio.core.domain.repository.RadioLinkRepository
import com.smoothradio.radio.core.domain.repository.RadioRepository
import com.smoothradio.radio.core.domain.usecase.ProcessRemoteLinksUseCase
import com.smoothradio.radio.core.domain.usecase.RecordAdShownUseCase
import com.smoothradio.radio.core.domain.usecase.ToggleFavoriteUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class DomainModule {

    @Provides
    @Singleton
    fun provideProcessRemoteLinksUseCase(
        radioRepository: RadioRepository,
        radioLinkRepository: RadioLinkRepository
    ): ProcessRemoteLinksUseCase {
        return ProcessRemoteLinksUseCase(radioRepository, radioLinkRepository)
    }

    @Provides
    @Singleton
    fun provideToggleFavoriteUseCase(
        radioRepository: RadioRepository
    ): ToggleFavoriteUseCase {
        return ToggleFavoriteUseCase(radioRepository)
    }

    @Provides
    @Singleton
    fun provideRecordAdShownUseCase(
        adSettingsRepository: AdSettingsRepository
    ): RecordAdShownUseCase {
        return RecordAdShownUseCase(adSettingsRepository)
    }
}