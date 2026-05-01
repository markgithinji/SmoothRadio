package com.smoothradio.radio.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.google.firebase.firestore.FirebaseFirestore
import com.smoothradio.radio.core.data.local.AppDatabase
import com.smoothradio.radio.core.data.local.RadioStationDao
import com.smoothradio.radio.core.data.repository.DefaultAdSettingsRepository
import com.smoothradio.radio.core.data.repository.DefaultRadioLinkRepository
import com.smoothradio.radio.core.data.repository.DefaultRadioRepository
import com.smoothradio.radio.core.data.repository.DefaultViewPreferenceRepository
import com.smoothradio.radio.core.domain.repository.AdSettingsRepository
import com.smoothradio.radio.core.domain.repository.RadioLinkRepository
import com.smoothradio.radio.core.domain.repository.RadioRepository
import com.smoothradio.radio.core.domain.repository.ViewPreferenceRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class CoreDataModule {

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

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("preferences") }
        )
    }

    @Provides
    @Singleton
    fun provideAdSettingsRepository(
        dataStore: DataStore<Preferences>
    ): AdSettingsRepository {
        return DefaultAdSettingsRepository(dataStore)
    }

    @Provides
    @Singleton
    fun provideViewPreferenceRepository(
        dataStore: DataStore<Preferences>
    ): ViewPreferenceRepository {
        return DefaultViewPreferenceRepository(dataStore)
    }
}
