package com.smoothradio.radio.service.playback

import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.service.util.playback.UltraFastLoadControl
import org.junit.Before
import org.junit.Test

class UltraFastLoadControlTest {

    private lateinit var loadControl: UltraFastLoadControl

    @Before
    fun setup() {
        loadControl = UltraFastLoadControl()
    }

    @Test
    fun `shouldStartPlayback should return true when buffer is 1500ms`() {
        // 1500ms = 1,500,000us
        val result = loadControl.shouldStartPlayback(
            bufferedDurationUs = 1_500_000L,
            playbackSpeed = 1.0f,
            rebuffering = false,
            targetLiveOffsetUs = 0L
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `shouldStartPlayback should return false when buffer is less than 1500ms`() {
        val result = loadControl.shouldStartPlayback(
            bufferedDurationUs = 1_400_000L,
            playbackSpeed = 1.0f,
            rebuffering = false,
            targetLiveOffsetUs = 0L
        )
        assertThat(result).isFalse()
    }

    @Test
    fun `shouldStartPlayback should require 3000ms when rebuffering`() {
        // 3000ms = 3,000,000us
        val resultSuccess = loadControl.shouldStartPlayback(
            bufferedDurationUs = 3_000_000L,
            playbackSpeed = 1.0f,
            rebuffering = true,
            targetLiveOffsetUs = 0L
        )
        val resultFail = loadControl.shouldStartPlayback(
            bufferedDurationUs = 2_900_000L,
            playbackSpeed = 1.0f,
            rebuffering = true,
            targetLiveOffsetUs = 0L
        )
        
        assertThat(resultSuccess).isTrue()
        assertThat(resultFail).isFalse()
    }
}
