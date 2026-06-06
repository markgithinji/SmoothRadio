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
import com.smoothradio.radio.service.util.proxy.LocalAudioProxy
import com.smoothradio.radio.service.util.proxy.ProxyDataSource
import com.smoothradio.radio.service.util.playback.UltraFastLoadControl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
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
        exoPlayer: ExoPlayer
    ): CastPlayer? {
        return castContext?.let {
            CastPlayer.Builder(context)
                .setLocalPlayer(exoPlayer)
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
    fun provideLocalAudioProxy(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): LocalAudioProxy =
        LocalAudioProxy(context.cacheDir, Dispatchers.IO, okHttpClient)

    @Provides
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        audioAttributes: AudioAttributes,
        dataSourceFactory: DataSource.Factory,
        localAudioProxy: LocalAudioProxy
    ): ExoPlayer {
        val extractorsFactory = DefaultExtractorsFactory()
            .setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)
            .setAdtsExtractorFlags(AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)

        // Use our custom ProxyDataSource to bypass HTTP layer for local proxy
        val proxyDataSourceFactory =
            ProxyDataSource.Factory(context, localAudioProxy, dataSourceFactory)

        val mediaSourceFactory = DefaultMediaSourceFactory(context, extractorsFactory)
            .setDataSourceFactory(proxyDataSourceFactory)

        // Configure Ultra-Fast LoadControl
        val loadControl = UltraFastLoadControl()

        return ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setLivePlaybackSpeedControl(
                DefaultLivePlaybackSpeedControl.Builder()
                    .setFallbackMinPlaybackSpeed(1.0f)
                    .setFallbackMaxPlaybackSpeed(1.0f)
                    .build()
            )
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build()
    }
}
