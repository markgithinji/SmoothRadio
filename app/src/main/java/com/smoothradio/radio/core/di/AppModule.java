package com.smoothradio.radio.core.di;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.room.Room;

import com.google.firebase.firestore.FirebaseFirestore;
import com.smoothradio.radio.core.data.local.AppDatabase;
import com.smoothradio.radio.core.data.local.RadioStationDao;
import com.smoothradio.radio.core.util.PlayerManager;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

import javax.inject.Singleton;

@Module
@InstallIn(SingletonComponent.class)
public class AppModule {

    @Provides
    @Singleton
    public AppDatabase provideDatabase(@ApplicationContext Context context) {
        return Room.databaseBuilder(context, AppDatabase.class, "radio_db")
                .fallbackToDestructiveMigration()
                .build();
    }

    @Provides
    public RadioStationDao provideRadioStationDao(AppDatabase database) {
        return database.radioStationDao();
    }

    @Provides
    @Singleton
    public FirebaseFirestore provideFirebaseFirestore() {
        return FirebaseFirestore.getInstance();
    }
}
