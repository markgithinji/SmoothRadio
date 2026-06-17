package com.smoothradio.radio.core.di

import com.smoothradio.radio.core.data.di.CoreDataModule
import com.smoothradio.radio.core.data.repository.FakeAdSettingsRepository
import com.smoothradio.radio.core.data.repository.FakeEqualizerRepository
import com.smoothradio.radio.core.data.repository.FakePlaybackStateRepository
import com.smoothradio.radio.core.data.repository.FakeFirebaseRepository
import com.smoothradio.radio.core.data.repository.FakeRadioRepository
import com.smoothradio.radio.core.data.repository.FakeViewPreferenceRepository
import com.smoothradio.radio.core.domain.repository.AdSettingsRepository
import com.smoothradio.radio.core.domain.repository.EqualizerRepository
import com.smoothradio.radio.core.domain.repository.PlaybackStateRepository
import com.smoothradio.radio.core.domain.repository.FirebaseRepository
import com.smoothradio.radio.core.domain.repository.RadioRepository
import com.smoothradio.radio.core.domain.repository.ViewPreferenceRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [CoreDataModule::class]
)
object FakeDataModule {
    @Provides
    @Singleton
    fun provideRadioRepository(): RadioRepository = FakeRadioRepository()

    @Provides
    @Singleton
    fun provideFirebaseRepository(): FirebaseRepository = FakeFirebaseRepository()

    @Provides
    @Singleton
    fun provideAdSettingsRepository(): AdSettingsRepository = FakeAdSettingsRepository()

    @Provides
    @Singleton
    fun provideViewPreferenceRepository(): ViewPreferenceRepository = FakeViewPreferenceRepository()

    @Provides
    @Singleton
    fun provideEqualizerRepository(): EqualizerRepository = FakeEqualizerRepository()

    @Provides
    @Singleton
    fun providePlaybackStateRepository(): PlaybackStateRepository = FakePlaybackStateRepository()
}
