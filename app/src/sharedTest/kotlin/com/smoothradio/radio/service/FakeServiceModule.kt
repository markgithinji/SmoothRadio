package com.smoothradio.radio.service

import android.content.Context
import android.os.Looper
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
import androidx.media3.datasource.DefaultDataSource
import com.smoothradio.radio.service.util.proxy.AudioProxy
import com.smoothradio.radio.service.util.proxy.DefaultAudioProxy
import kotlinx.coroutines.Dispatchers
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
        .setLooper(Looper.getMainLooper())
        .build()

    @Provides
    @Singleton
    fun provideAudioProxy(
        @ApplicationContext context: Context
    ): AudioProxy = DefaultAudioProxy(
        cacheDir = context.cacheDir,
        ioDispatcher = Dispatchers.IO,
        okHttpClient = OkHttpClient()
    )

    @Provides
    fun provideDataSourceFactory(
        @ApplicationContext context: Context
    ): DataSource.Factory = DataSource.Factory { 
        DefaultDataSource.Factory(context).createDataSource()
    }
}
