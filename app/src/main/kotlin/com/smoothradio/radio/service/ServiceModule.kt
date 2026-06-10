@file:OptIn(UnstableApi::class)

package com.smoothradio.radio.service

import android.content.Context
import androidx.media3.cast.CastPlayer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.ts.AdtsExtractor
import com.google.android.gms.cast.framework.CastContext
import com.smoothradio.radio.core.util.PlaybackConstants
import com.smoothradio.radio.service.util.playback.UltraFastLoadControl
import com.smoothradio.radio.service.util.proxy.AudioProxy
import com.smoothradio.radio.service.util.proxy.DefaultAudioProxy
import com.smoothradio.radio.service.util.proxy.ProxyDataSource
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    fun provideCastContext(@ApplicationContext context: Context): CastContext? {
        return try {
            CastContext.getSharedInstance(context)
        } catch (e: Exception) {
            null
        }
    }

    @Provides
    fun provideCastPlayer(
        @ApplicationContext context: Context,
        castContext: CastContext?,
        exoPlayer: Lazy<ExoPlayer>
    ): CastPlayer? {
        return castContext?.let {
            CastPlayer.Builder(context)
                .setLocalPlayer(exoPlayer.get())
                .build()
        }
    }

    @Provides
    @Singleton
    fun provideAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    @Provides
    @Singleton
    fun provideDataSourceFactory(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): DataSource.Factory {
        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)

        return DefaultDataSource.Factory(context, httpDataSourceFactory)
    }

    @Provides
    @Singleton
    fun provideAudioProxy(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): AudioProxy {
        val cacheDir = File(context.cacheDir, "audio_proxy")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        return DefaultAudioProxy(cacheDir, Dispatchers.IO, okHttpClient)
    }

    @Provides
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        audioAttributes: AudioAttributes,
        dataSourceFactory: DataSource.Factory,
        audioProxy: AudioProxy
    ): ExoPlayer {
        val extractorsFactory = DefaultExtractorsFactory()
            .setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)
            .setAdtsExtractorFlags(AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)

        // Use our custom ProxyDataSource to bypass HTTP layer for local proxy
        val proxyDataSourceFactory =
            ProxyDataSource.Factory(context, audioProxy, dataSourceFactory)

        val mediaSourceFactory = DefaultMediaSourceFactory(context, extractorsFactory)
            .setDataSourceFactory(proxyDataSourceFactory)

        // Configure Ultra-Fast LoadControl
        val loadControl = UltraFastLoadControl()

        return ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setDeviceVolumeControlEnabled(true)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setLivePlaybackSpeedControl(
                DefaultLivePlaybackSpeedControl.Builder()
                    .setFallbackMinPlaybackSpeed(1.0f)
                    .setFallbackMaxPlaybackSpeed(1.0f)
                    .build()
            )
            .setSeekBackIncrementMs(PlaybackConstants.SEEK_INCREMENT_MS)
            .setSeekForwardIncrementMs(PlaybackConstants.SEEK_INCREMENT_MS)
            .build()
    }
}
