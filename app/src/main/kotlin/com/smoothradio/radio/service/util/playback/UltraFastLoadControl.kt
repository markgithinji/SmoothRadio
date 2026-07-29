@file:OptIn(UnstableApi::class)

package com.smoothradio.radio.service.util.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.upstream.DefaultAllocator

/**
 * A custom LoadControl that prioritizes absolute minimal latency.
 * Forces ExoPlayer to start playing almost as soon as data starts arriving.
 */
class UltraFastLoadControl : DefaultLoadControl(
    /* allocator = */ DefaultAllocator(true, 65536),
    /* minBufferMs = */ 4000,
    /* minBufferForLocalPlaybackMs = */ 4000,
    /* maxBufferMs = */ 10000,
    /* maxBufferForLocalPlaybackMs = */ 10000,
    /* bufferForPlaybackMs = */ 1500,
    /* bufferForPlaybackForLocalPlaybackMs = */ 1500,
    /* bufferForPlaybackAfterRebufferMs = */ 3000,
    /* bufferForPlaybackAfterRebufferForLocalPlaybackMs = */ 3000,
    /* targetBufferBytes = */ -1,
    /* prioritizeTimeOverSizeThresholds = */ true,
    /* prioritizeTimeOverSizeThresholdsForLocalPlayback = */ true,
    /* backBufferDurationMs = */ 120000,
    /* retainBackBufferFromKeyframe = */ true
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
