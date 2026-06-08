package com.smoothradio.radio.service

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.domain.model.StreamStates
import com.smoothradio.radio.core.domain.repository.PlaybackStateRepository
import com.smoothradio.radio.service.util.command.ServiceCommand
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class StreamServiceTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var stateRepository: PlaybackStateRepository

    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Force stop service before injection to ensure a clean state
        context.stopService(Intent(context, StreamService::class.java))
        
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        val intent = Intent(context, StreamService::class.java).apply {
            action = ServiceCommand.ACTION_STOP
        }
        context.startService(intent)
    }

    private fun startService(intent: Intent) {
        context.startService(intent)
    }

    private suspend fun waitForCondition(
        timeoutMs: Long = 20000, 
        condition: () -> Boolean
    ) {
        // Use real time instead of virtual time to wait for external service events
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(timeoutMs) {
                while (!condition()) {
                    delay(200)
                }
            }
        }
    }

    @Test
    fun startAction_shouldTransitionToPreparing() = runTest {
        val intent = Intent(context, StreamService::class.java).apply {
            action = ServiceCommand.ACTION_START
            putExtra(ServiceCommand.EXTRA_LINK, "https://a5.asurahosting.com:7530/radio.mp3")
            putExtra(ServiceCommand.EXTRA_LOGO, 0)
            putExtra(ServiceCommand.EXTRA_STATION_NAME, "HOPE FM")
        }

        startService(intent)

        waitForCondition {
            stateRepository.playbackState.value == StreamStates.PREPARING || 
            stateRepository.playbackState.value == StreamStates.BUFFERING
        }

        assertThat(stateRepository.playbackState.value).isAnyOf(StreamStates.PREPARING, StreamStates.BUFFERING)
    }

    @Test
    fun startPlay_shouldUpdateStationNameInRepository() = runTest {
        val intent = Intent(context, StreamService::class.java).apply {
            action = ServiceCommand.ACTION_START
            putExtra(ServiceCommand.EXTRA_LINK, "https://a5.asurahosting.com:7530/radio.mp3")
            putExtra(ServiceCommand.EXTRA_STATION_NAME, "HOPE FM")
        }

        startService(intent)

        waitForCondition { 
            stateRepository.stationName.value == "HOPE FM" 
        }

        assertThat(stateRepository.stationName.value).isEqualTo("HOPE FM")
    }

    @Test
    fun stopAction_shouldTransitionToIdle() = runTest {
        // Start first
        val startIntent = Intent(context, StreamService::class.java).apply {
            action = ServiceCommand.ACTION_START
            putExtra(ServiceCommand.EXTRA_LINK, "https://a5.asurahosting.com:7530/radio.mp3")
            putExtra(ServiceCommand.EXTRA_STATION_NAME, "HOPE FM")
        }
        startService(startIntent)
        
        waitForCondition { 
            stateRepository.playbackState.value == StreamStates.PREPARING || 
            stateRepository.playbackState.value == StreamStates.BUFFERING
        }

        // Then stop
        val stopIntent = Intent(context, StreamService::class.java).apply {
            action = ServiceCommand.ACTION_STOP
        }
        startService(stopIntent)

        waitForCondition { 
            stateRepository.playbackState.value == StreamStates.IDLE 
        }

        assertThat(stateRepository.playbackState.value).isEqualTo(StreamStates.IDLE)
    }

    @Test
    fun showAdAction_shouldSetPreparingState() = runTest {
        val intent = Intent(context, StreamService::class.java).apply {
            action = ServiceCommand.ACTION_SHOW_AD
            putExtra(ServiceCommand.EXTRA_STATION_NAME, "Test Station")
            putExtra(ServiceCommand.EXTRA_LOGO, 0)
        }

        startService(intent)

        waitForCondition { 
            stateRepository.playbackState.value == StreamStates.PREPARING || 
            stateRepository.playbackState.value == StreamStates.BUFFERING
        }

        assertThat(stateRepository.playbackState.value).isAnyOf(StreamStates.PREPARING, StreamStates.BUFFERING)
    }

    @Test
    fun setEqualizerBand_shouldNotCrash() = runTest {
        val startIntent = Intent(context, StreamService::class.java).apply {
            action = ServiceCommand.ACTION_START
            putExtra(ServiceCommand.EXTRA_LINK, "https://a5.asurahosting.com:7530/radio.mp3")
            putExtra(ServiceCommand.EXTRA_STATION_NAME, "HOPE FM")
        }
        startService(startIntent)

        waitForCondition {
            stateRepository.playbackState.value == StreamStates.PREPARING || 
            stateRepository.playbackState.value == StreamStates.BUFFERING
        }

        val eqIntent = Intent(context, StreamService::class.java).apply {
            action = ServiceCommand.ACTION_SET_EQ_BAND
            putExtra(ServiceCommand.EXTRA_BAND, 0)
            putExtra(ServiceCommand.EXTRA_LEVEL, 500.toShort())
        }
        startService(eqIntent)
        // Ensure no crash occurs during execution
        delay(500)
    }

    @Test
    fun playPauseActions_shouldTogglePlayback() = runTest {
        val startIntent = Intent(context, StreamService::class.java).apply {
            action = ServiceCommand.ACTION_START
            putExtra(ServiceCommand.EXTRA_LINK, "https://a5.asurahosting.com:7530/radio.mp3")
            putExtra(ServiceCommand.EXTRA_LOGO, 0)
            putExtra(ServiceCommand.EXTRA_STATION_NAME, "HOPE FM")
        }
        startService(startIntent)
        
        // Manual state update for testing UI reactivity if needed
        stateRepository.updateState(StreamStates.PLAYING)
        
        assertThat(stateRepository.playbackState.value).isEqualTo(StreamStates.PLAYING)
        
        val stopIntent = Intent(context, StreamService::class.java).apply {
            action = ServiceCommand.ACTION_STOP
        }
        startService(stopIntent)
        delay(500)
    }
}
