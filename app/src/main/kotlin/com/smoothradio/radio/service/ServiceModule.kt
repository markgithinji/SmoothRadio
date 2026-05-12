@file:OptIn(UnstableApi::class)

package com.smoothradio.radio.service

import android.content.Context
import androidx.media3.cast.CastPlayer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
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
    @Singleton
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
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        audioAttributes: AudioAttributes
    ): ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(audioAttributes, true)
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()
}
