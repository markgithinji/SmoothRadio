package com.smoothradio.radio.core.di

import com.smoothradio.radio.core.data.di.CoreDataModule
import com.smoothradio.radio.core.data.repository.FakeAdSettingsRepositoryAndroidTest
import com.smoothradio.radio.core.data.repository.FakeEqualizerRepositoryAndroidTest
import com.smoothradio.radio.core.data.repository.FakePlaybackStateRepositoryAndroidTest
import com.smoothradio.radio.core.data.repository.FakeFirebaseRepositoryAndroidTest
import com.smoothradio.radio.core.data.repository.FakeRadioRepositoryAndroidTest
import com.smoothradio.radio.core.data.repository.FakeViewPreferenceRepositoryAndroidTest
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
    fun provideRadioRepository(): RadioRepository = FakeRadioRepositoryAndroidTest()

    @Provides
    @Singleton
    fun provideFirebaseRepository(): FirebaseRepository = FakeFirebaseRepositoryAndroidTest()

    @Provides
    @Singleton
    fun provideAdSettingsRepository(): AdSettingsRepository = FakeAdSettingsRepositoryAndroidTest()

    @Provides
    @Singleton
    fun provideViewPreferenceRepository(): ViewPreferenceRepository = FakeViewPreferenceRepositoryAndroidTest()

    @Provides
    @Singleton
    fun provideEqualizerRepository(): EqualizerRepository = FakeEqualizerRepositoryAndroidTest()

    @Provides
    @Singleton
    fun providePlaybackStateRepository(): PlaybackStateRepository = FakePlaybackStateRepositoryAndroidTest()
}
