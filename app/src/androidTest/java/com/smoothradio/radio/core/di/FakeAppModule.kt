package com.smoothradio.radio.core.di

import com.smoothradio.radio.core.data.repository.FakeRadioLinkRepositoryAndroidTest
import com.smoothradio.radio.core.data.repository.FakeRadioRepositoryAndroidTest
import com.smoothradio.radio.core.data.repository.RadioLinkRepository
import com.smoothradio.radio.core.data.repository.RadioRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AppModule::class]
)
object FakeAppModule {

    @Provides
    @Singleton
    fun provideRadioRepository(): RadioRepository = FakeRadioRepositoryAndroidTest()

    @Provides
    @Singleton
    fun provideRadioLinkRepository(): RadioLinkRepository = FakeRadioLinkRepositoryAndroidTest()
}
