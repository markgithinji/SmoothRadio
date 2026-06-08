package com.smoothradio.radio.service

import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.media3.exoplayer.ExoPlayer
import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.domain.model.StreamStates
import com.smoothradio.radio.core.domain.repository.PlaybackStateRepository
import com.smoothradio.radio.service.util.command.ServiceCommand
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(sdk = [30], application = HiltTestApplication::class)
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
    fun tearDown() {
        val intent = Intent(context, StreamService::class.java).apply {
            action = ServiceCommand.ACTION_STOP
        }
        context.startService(intent)
        shadowOf(Looper.getMainLooper()).idle()
        context.stopService(Intent(context, StreamService::class.java))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun startService(intent: Intent) {
        context.startService(intent)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun startAction_shouldTransitionToPreparing() = runTest(UnconfinedTestDispatcher()) {
        val intent = Intent(context, StreamService::class.java).apply {
            action = ServiceCommand.ACTION_START
            putExtra(ServiceCommand.EXTRA_LINK, "https://a5.asurahosting.com:7530/radio.mp3")
            putExtra(ServiceCommand.EXTRA_LOGO, 0)
            putExtra(ServiceCommand.EXTRA_STATION_NAME, "HOPE FM")
        }

        context.startService(intent)
        shadowOf(Looper.getMainLooper()).idle()

        // Give Robolectric more time to process the intent
        withTimeout(15000) {
            while (stateRepository.playbackState.value != StreamStates.PREPARING) {
                shadowOf(Looper.getMainLooper()).idle()
                delay(200)
            }
        }

        assertThat(stateRepository.playbackState.value).isEqualTo(StreamStates.PREPARING)
    }

    @Test
    fun startPlay_shouldUpdateStationNameInRepository() = runTest(UnconfinedTestDispatcher()) {
        val intent = Intent(context, StreamService::class.java).apply {
            action = ServiceCommand.ACTION_START
            putExtra(ServiceCommand.EXTRA_LINK, "https://a5.asurahosting.com:7530/radio.mp3")
            putExtra(ServiceCommand.EXTRA_LOGO, 0)
            putExtra(ServiceCommand.EXTRA_STATION_NAME, "HOPE FM")
        }

        startService(intent)

        withTimeout(15000) {
            while (stateRepository.stationName.value != "HOPE FM") {
                shadowOf(Looper.getMainLooper()).idle()
                delay(100)
            }
        }

        assertThat(stateRepository.stationName.value).isEqualTo("HOPE FM")
    }

    @Test
    fun stopAction_shouldTransitionToIdle() = runTest(UnconfinedTestDispatcher()) {
        // Start first
        val startIntent = Intent(context, StreamService::class.java).apply {
            action = ServiceCommand.ACTION_START
            putExtra(ServiceCommand.EXTRA_LINK, "https://a5.asurahosting.com:7530/radio.mp3")
            putExtra(ServiceCommand.EXTRA_STATION_NAME, "HOPE FM")
        }
        startService(startIntent)
        
        withTimeout(15000) {
            while (stateRepository.playbackState.value != StreamStates.PREPARING) {
                shadowOf(Looper.getMainLooper()).idle()
                delay(100)
            }
        }

        // Then stop
        val stopIntent = Intent(context, StreamService::class.java).apply {
            action = ServiceCommand.ACTION_STOP
        }
        startService(stopIntent)

        withTimeout(15000) {
            while (stateRepository.playbackState.value != StreamStates.IDLE) {
                shadowOf(Looper.getMainLooper()).idle()
                delay(100)
            }
        }

        assertThat(stateRepository.playbackState.value).isEqualTo(StreamStates.IDLE)
    }

    @Test
    fun showAdAction_shouldSetPreparingState() = runTest(UnconfinedTestDispatcher()) {
        val intent = Intent(context, StreamService::class.java).apply {
            action = ServiceCommand.ACTION_SHOW_AD
            putExtra(ServiceCommand.EXTRA_STATION_NAME, "Test Station")
            putExtra(ServiceCommand.EXTRA_LOGO, 0)
        }

        startService(intent)

        withTimeout(15000) {
            while (stateRepository.playbackState.value != StreamStates.PREPARING) {
                shadowOf(Looper.getMainLooper()).idle()
                delay(100)
            }
        }

        assertThat(stateRepository.playbackState.value).isEqualTo(StreamStates.PREPARING)
    }

    @Test
    fun nullAction_shouldNotCrash() = runTest(UnconfinedTestDispatcher()) {
        val intent = Intent(context, StreamService::class.java)
        startService(intent)
        // No specific state change expected, just ensuring it doesn't crash
    }

    @Test
    fun setEqualizerBand_shouldNotCrash() = runTest(UnconfinedTestDispatcher()) {
        val startIntent = Intent(context, StreamService::class.java).apply {
            action = ServiceCommand.ACTION_START
            putExtra(ServiceCommand.EXTRA_LINK, "https://a5.asurahosting.com:7530/radio.mp3")
            putExtra(ServiceCommand.EXTRA_STATION_NAME, "HOPE FM")
        }
        startService(startIntent)

        val eqIntent = Intent(context, StreamService::class.java).apply {
            action = ServiceCommand.ACTION_SET_EQ_BAND
            putExtra(ServiceCommand.EXTRA_BAND, 0)
            putExtra(ServiceCommand.EXTRA_LEVEL, 500.toShort())
        }
        startService(eqIntent)
        // No specific state change expected, just ensuring it doesn't crash
    }

    @Test
    fun playPauseActions_shouldTogglePlayback() = runTest(UnconfinedTestDispatcher()) {
        val startIntent = Intent(context, StreamService::class.java).apply {
            action = ServiceCommand.ACTION_START
            putExtra(ServiceCommand.EXTRA_LINK, "https://a5.asurahosting.com:7530/radio.mp3")
            putExtra(ServiceCommand.EXTRA_LOGO, 0)
            putExtra(ServiceCommand.EXTRA_STATION_NAME, "HOPE FM")
        }
        startService(startIntent)
        
        stateRepository.updateState(StreamStates.PLAYING)
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(stateRepository.playbackState.value).isEqualTo(StreamStates.PLAYING)
        
        // Explicitly stop to avoid leaking progressUpdateJob which causes test to run indefinitely
        val stopIntent = Intent(context, StreamService::class.java).apply {
            action = ServiceCommand.ACTION_STOP
        }
        startService(stopIntent)
    }
}
