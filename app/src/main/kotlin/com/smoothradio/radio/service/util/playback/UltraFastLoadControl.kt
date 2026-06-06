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
    /* minBufferMs = */ 2000,
    /* minBufferForLocalPlaybackMs = */ 2000, // minBufferMs (standard, local)
    /* maxBufferMs = */ 10000,
    /* maxBufferForLocalPlaybackMs = */ 10000, // maxBufferMs
    /* bufferForPlaybackMs = */ 500,
    /* bufferForPlaybackForLocalPlaybackMs = */ 500, // bufferForPlaybackMs (CRITICAL: Only wait 0.5s to start)
    /* bufferForPlaybackAfterRebufferMs = */ 1000,
    /* bufferForPlaybackAfterRebufferForLocalPlaybackMs = */ 1000, // bufferForPlaybackAfterRebufferMs
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
        // FORCE start if we have 500ms
        val minBufferToStartUs = 500_000L
        return if (rebuffering) {
            bufferedDurationUs >= 1_000_000L
        } else {
            bufferedDurationUs >= minBufferToStartUs
        }
    }
}
