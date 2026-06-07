package com.smoothradio.radio.service

import android.content.Context
import androidx.media3.cast.CastPlayer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.gms.cast.framework.CastContext
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import androidx.media3.datasource.DataSource
import com.smoothradio.radio.service.util.proxy.LocalAudioProxy
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [ServiceModule::class]
)
object FakeServiceModule {

    @Provides
    @Singleton
    fun provideCastContext(): CastContext? = null

    @Provides
    @Singleton
    fun provideCastPlayer(): CastPlayer? = null

    @Provides
    @Singleton
    fun provideAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    @Provides
    @Singleton
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        audioAttributes: AudioAttributes
    ): ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(audioAttributes, true)
        .setLooper(android.os.Looper.getMainLooper())
        .build()

    @Provides
    @Singleton
    fun provideLocalAudioProxy(
        @ApplicationContext context: Context
    ): LocalAudioProxy = LocalAudioProxy(
        cacheDir = context.cacheDir,
        ioDispatcher = kotlinx.coroutines.Dispatchers.IO,
        okHttpClient = OkHttpClient()
    )

    @Provides
    fun provideDataSourceFactory(
        @ApplicationContext context: Context
    ): DataSource.Factory = DataSource.Factory { 
        androidx.media3.datasource.DefaultDataSource.Factory(context).createDataSource()
    }
}
