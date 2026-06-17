@file:OptIn(UnstableApi::class)

package com.smoothradio.radio.service.util.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.annotation.OptIn

/**
 * A custom LoadControl that prioritizes absolute minimal latency.
 * Forces ExoPlayer to start playing almost as soon as data starts arriving.
 */
class UltraFastLoadControl : DefaultLoadControl(
    /* allocator = */ DefaultAllocator(true, 65536),
    /* minBufferMs = */ 4000,
    /* minBufferForLocalPlaybackMs = */ 4000, // Increased from 2000ms
    /* maxBufferMs = */ 10000,
    /* maxBufferForLocalPlaybackMs = */ 10000, // maxBufferMs
    /* bufferForPlaybackMs = */ 1500,
    /* bufferForPlaybackForLocalPlaybackMs = */ 1500, // Increased to 1.5s to prevent flicker
    /* bufferForPlaybackAfterRebufferMs = */ 3000,
    /* bufferForPlaybackAfterRebufferForLocalPlaybackMs = */ 3000, // Increased to 3s for stability
    /* targetBufferBytes = */ -1, // targetBufferBytes
    /* prioritizeTimeOverSizeThresholds = */ true,
    /* prioritizeTimeOverSizeThresholdsForLocalPlayback = */ true, // prioritizeTimeOverSizeThresholds
    /* backBufferDurationMs = */ 120000, // backBufferDurationMs
    /* retainBackBufferFromKeyframe = */ true // retainBackBufferFromKeyframe
) {

    @Deprecated("Deprecated in Java")
    override fun shouldStartPlayback(
        bufferedDurationUs: Long,
        playbackSpeed: Float,
        rebuffering: Boolean,
        targetLiveOffsetUs: Long
    ): Boolean {
        // FORCE start if we have 1500ms
        val minBufferToStartUs = 1_500_000L
        return if (rebuffering) {
            bufferedDurationUs >= 3_000_000L
        } else {
            bufferedDurationUs >= minBufferToStartUs
        }
    }
}
