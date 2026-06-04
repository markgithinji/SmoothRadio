@file:OptIn(UnstableApi::class)

package com.smoothradio.radio.service.util

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.annotation.OptIn

/**
 * A custom LoadControl that prioritizes absolute minimal latency.
 * Forces ExoPlayer to start playing almost as soon as data starts arriving.
 */
class UltraFastLoadControl : DefaultLoadControl(
    DefaultAllocator(true, 65536),
    2000, 2000, // minBufferMs (standard, local)
    10000, 10000, // maxBufferMs
    500, 500,    // bufferForPlaybackMs (CRITICAL: Only wait 0.5s to start)
    1000, 1000,  // bufferForPlaybackAfterRebufferMs
    -1,          // targetBufferBytes
    true, true,  // prioritizeTimeOverSizeThresholds
    120000,      // backBufferDurationMs
    true         // retainBackBufferFromKeyframe
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
