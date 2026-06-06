package com.smoothradio.radio.service.util

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Type-safe snapshot of the current playback progress and buffer state.
 */
data class ProgressSnapshot(
    val position: Long,
    val duration: Long,
    val minPosition: Long,
    val loadedPosition: Long,
    val loadingProgress: Float
)

@Singleton
class PlaybackProgressCalculator @Inject constructor() {
    /**
     * Pure math logic to calculate UI-friendly positions and progress based on raw byte offsets.
     */
    fun calculate(
        currentPosition: Long,
        totalBytesWritten: Long,
        totalBytesDropped: Long,
        totalBytesReceived: Long,
        estimatedBytesPerMs: Double,
        isHls: Boolean,
        totalCapacityBytes: Long,
        isBuffering: Boolean
    ): ProgressSnapshot {
        val droppedDur = (totalBytesDropped / estimatedBytesPerMs).toLong()
        val loadedDur = (totalBytesWritten / estimatedBytesPerMs).toLong()

        // FIXED-WIDTH SLIDING WINDOW:
        val bufferCapacityMs = totalCapacityBytes / estimatedBytesPerMs.coerceAtLeast(4.0)
        val displayDur = droppedDur + bufferCapacityMs.toLong()

        val safetyBuffer = if (isHls) 12000L else 2000L
        
        // We report the "Safe Live Edge" as the loaded position to the UI.
        val safeLoadedPos = (loadedDur - safetyBuffer).coerceAtLeast(droppedDur)

        val loadingProgress = if (isBuffering) {
            // Target 2s of buffer for initial playback
            val targetMs = 2000.0
            val currentMs = totalBytesReceived.toDouble() / estimatedBytesPerMs.coerceAtLeast(1.0)
            val progress = (currentMs / targetMs).toFloat().coerceIn(0f, 1f)
            
            // Add a small baseline (5%) once we start to show "Connecting..." activity
            if (progress > 0 || totalBytesReceived > 0) 0.05f + (progress * 0.95f) else 0f
        } else {
            1f
        }

        return ProgressSnapshot(
            position = if (currentPosition < 0) 0 else currentPosition,
            duration = displayDur,
            minPosition = droppedDur,
            loadedPosition = safeLoadedPos,
            loadingProgress = loadingProgress
        )
    }
}
