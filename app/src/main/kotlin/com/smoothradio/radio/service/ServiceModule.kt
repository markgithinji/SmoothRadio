@file:OptIn(UnstableApi::class)

package com.smoothradio.radio.service

import android.content.Context
import androidx.media3.cast.CastPlayer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.ts.AdtsExtractor
import com.smoothradio.radio.core.util.LocalAudioProxy
import com.google.android.gms.cast.framework.CastContext
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
    fun provideDataSourceFactory(@ApplicationContext context: Context): DataSource.Factory {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
        
        return DefaultDataSource.Factory(context, httpDataSourceFactory)
    }

    @Provides
    @Singleton
    fun provideLocalAudioProxy(@ApplicationContext context: Context): LocalAudioProxy = 
        LocalAudioProxy(context)

    @Provides
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        audioAttributes: AudioAttributes,
        dataSourceFactory: DataSource.Factory
    ): ExoPlayer {
        val extractorsFactory = DefaultExtractorsFactory()
            .setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)
            .setAdtsExtractorFlags(AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)

        val mediaSourceFactory = DefaultMediaSourceFactory(context, extractorsFactory)
            .setDataSourceFactory(dataSourceFactory)

        // Configure LoadControl for live-streaming performance
        val loadControl = DefaultLoadControl.Builder()
            .setBackBuffer(1500000, true) // 25 minute back buffer
            .setBufferDurationsMs(
                10000, // min buffer (10s)
                20000, // max buffer (20s)
                2000,  // buffer for playback (2s)
                3000   // buffer after rebuffer (3s)
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        return ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build()
    }
}
