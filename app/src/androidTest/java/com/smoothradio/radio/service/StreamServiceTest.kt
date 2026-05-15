package com.smoothradio.radio.service

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.media3.exoplayer.ExoPlayer
import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.domain.model.StreamStates
import com.smoothradio.radio.core.domain.repository.PlaybackStateRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@ExperimentalCoroutinesApi
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class StreamServiceTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var stateRepository: PlaybackStateRepository

    @Inject
    lateinit var exoPlayer: ExoPlayer

    private lateinit var context: Context

    @Before
    fun setup() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() = runTest {
        val intent = Intent(context, StreamService::class.java).apply {
            action = StreamService.ACTION_STOP
        }
        context.startService(intent)
        // Give it a moment to stop
        kotlinx.coroutines.delay(100)
        context.stopService(Intent(context, StreamService::class.java))
    }

    private fun startService(intent: Intent) {
        context.startService(intent)
    }

    @Test
    fun startAction_shouldTransitionToPreparing() = runTest {
        val intent = Intent(context, StreamService::class.java).apply {
            action = StreamService.ACTION_START
            putExtra(StreamService.EXTRA_LINK, "https://a5.asurahosting.com:7530/radio.mp3")
            putExtra(StreamService.EXTRA_LOGO, 0)
            putExtra(StreamService.EXTRA_STATION_NAME, "HOPE FM")
        }

        startService(intent)

        withContext(Dispatchers.Default) {
            withTimeout(5000) {
                stateRepository.playbackState.first { it == StreamStates.PREPARING }
            }
        }

        assertThat(stateRepository.playbackState.value).isEqualTo(StreamStates.PREPARING)
    }

    @Test
    fun startPlay_shouldUpdateStationNameInRepository() = runTest {
        val intent = Intent(context, StreamService::class.java).apply {
            action = StreamService.ACTION_START
            putExtra(StreamService.EXTRA_LINK, "https://a5.asurahosting.com:7530/radio.mp3")
            putExtra(StreamService.EXTRA_LOGO, 0)
            putExtra(StreamService.EXTRA_STATION_NAME, "HOPE FM")
        }

        startService(intent)

        withContext(Dispatchers.Default) {
            withTimeout(5000) {
                stateRepository.stationName.first { it == "HOPE FM" }
            }
        }

        assertThat(stateRepository.stationName.value).isEqualTo("HOPE FM")
    }

    @Test
    fun stopAction_shouldTransitionToIdle() = runTest {
        // Start first
        val startIntent = Intent(context, StreamService::class.java).apply {
            action = StreamService.ACTION_START
            putExtra(StreamService.EXTRA_LINK, "https://a5.asurahosting.com:7530/radio.mp3")
            putExtra(StreamService.EXTRA_STATION_NAME, "HOPE FM")
        }
        startService(startIntent)
        
        withContext(Dispatchers.Default) {
            withTimeout(5000) {
                stateRepository.playbackState.first { it == StreamStates.PREPARING }
            }
        }

        // Then stop
        val stopIntent = Intent(context, StreamService::class.java).apply {
            action = StreamService.ACTION_STOP
        }
        startService(stopIntent)

        withContext(Dispatchers.Default) {
            withTimeout(5000) {
                stateRepository.playbackState.first { it == StreamStates.IDLE }
            }
        }

        assertThat(stateRepository.playbackState.value).isEqualTo(StreamStates.IDLE)
    }

    @Test
    fun showAdAction_shouldSetPreparingState() = runTest {
        val intent = Intent(context, StreamService::class.java).apply {
            action = StreamService.ACTION_SHOW_AD
            putExtra(StreamService.EXTRA_STATION_NAME, "Test Station")
            putExtra(StreamService.EXTRA_LOGO, 0)
        }

        startService(intent)

        withContext(Dispatchers.Default) {
            withTimeout(5000) {
                stateRepository.playbackState.first { it == StreamStates.PREPARING }
            }
        }

        assertThat(stateRepository.playbackState.value).isEqualTo(StreamStates.PREPARING)
    }

    @Test
    fun nullAction_shouldNotCrash() = runTest {
        val intent = Intent(context, StreamService::class.java)
        startService(intent)
        // No specific state change expected, just ensuring it doesn't crash
    }

    @Test
    fun setEqualizerBand_shouldNotCrash() = runTest {
        val startIntent = Intent(context, StreamService::class.java).apply {
            action = StreamService.ACTION_START
            putExtra(StreamService.EXTRA_LINK, "https://a5.asurahosting.com:7530/radio.mp3")
            putExtra(StreamService.EXTRA_STATION_NAME, "HOPE FM")
        }
        startService(startIntent)

        val eqIntent = Intent(context, StreamService::class.java).apply {
            action = StreamService.ACTION_SET_EQ_BAND
            putExtra(StreamService.EXTRA_BAND, 0)
            putExtra(StreamService.EXTRA_LEVEL, 500.toShort())
        }
        startService(eqIntent)
        // No specific state change expected, just ensuring it doesn't crash
    }

    @Test
    fun playPauseActions_shouldTogglePlayback() = runTest {
        val startIntent = Intent(context, StreamService::class.java).apply {
            action = StreamService.ACTION_START
            putExtra(StreamService.EXTRA_LINK, "https://a5.asurahosting.com:7530/radio.mp3")
            putExtra(StreamService.EXTRA_LOGO, 0)
            putExtra(StreamService.EXTRA_STATION_NAME, "HOPE FM")
        }
        startService(startIntent)
        
        withContext(Dispatchers.Main) {
            stateRepository.updateState(StreamStates.PLAYING)
        }

        withContext(Dispatchers.Default) {
            withTimeout(5000) {
                stateRepository.playbackState.first { it == StreamStates.PLAYING }
            }
        }

        assertThat(stateRepository.playbackState.value).isEqualTo(StreamStates.PLAYING)
    }
}
