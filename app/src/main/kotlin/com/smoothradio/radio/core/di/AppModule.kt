package com.smoothradio.radio.core.di

import android.content.Context
import androidx.room.Room
import com.google.firebase.firestore.FirebaseFirestore
import com.smoothradio.radio.core.data.local.AppDatabase
import com.smoothradio.radio.core.data.local.RadioStationDao
import com.smoothradio.radio.core.data.repository.DefaultRadioLinkRepository
import com.smoothradio.radio.core.data.repository.DefaultRadioRepository
import com.smoothradio.radio.core.domain.repository.RadioLinkRepository
import com.smoothradio.radio.core.domain.repository.RadioRepository
import com.smoothradio.radio.core.domain.usecase.ProcessRemoteLinksUseCase
import com.smoothradio.radio.core.util.PlayerManager
import com.smoothradio.radio.feature.radio_list.usecase.DefaultProcessRemoteLinksUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "radio_db")
            .build()

    @Provides
    fun provideRadioStationDao(database: AppDatabase): RadioStationDao = database.radioStationDao()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun providePlayerManager() = PlayerManager()

    @Provides
    @Singleton
    fun provideProcessRemoteLinksUseCase(
        radioRepository: RadioRepository
    ): ProcessRemoteLinksUseCase {
        return DefaultProcessRemoteLinksUseCase(radioRepository)
    }

    @Provides
    @Singleton
    fun provideRadioRepository(dao: RadioStationDao): RadioRepository {
        return DefaultRadioRepository(dao)
    }

    @Provides
    @Singleton
    fun provideRadioLinkRepository(
        @ApplicationContext context: Context,
        firestore: FirebaseFirestore
    ): RadioLinkRepository {
        return DefaultRadioLinkRepository(context, firestore)
    }
}


