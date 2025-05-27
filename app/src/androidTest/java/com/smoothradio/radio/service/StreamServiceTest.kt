package com.smoothradio.radio.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onIdle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.testutil.SimpleFlagIdlingResource
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class StreamServiceTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    private lateinit var context: Context
    private lateinit var receiver: BroadcastReceiver
    private val receivedStates = mutableListOf<String>()

    private var playbackCompleted = false
    private val idlingResource = SimpleFlagIdlingResource { playbackCompleted }
    private val radioStation: RadioStation =
        RadioStation(
            id = 2,
            logoResource = 0,
            stationName = "HOPE FM",
            frequency = "88.8",
            location = "City",
            streamLink = "https://a5.asurahosting.com:7530/radio.mp3",
            isPlaying = false,
            isFavorite = false,
            orderIndex = 0
        )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        Espresso.registerIdlingResources(idlingResource)

        receivedStates.clear()
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val state = intent?.getStringExtra(StreamService.EXTRA_STATE)
                if (state != null) {
                    receivedStates.add(state)
                    if (state == StreamService.StreamStates.PLAYING) {
                        playbackCompleted = true
                    }
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(StreamService.ACTION_EVENT_CHANGE))
    }

    @After
    fun tearDown() {
        context.unregisterReceiver(receiver)
    }

    @Test
    fun startService_shouldBroadcastPreparingState() {
        startService()
        // Wait until broadcast received or timeout
        onIdle()
        assertThat(receivedStates).contains(StreamService.StreamStates.PREPARING)
    }

    @Test
    fun sendBroadcast_actionGetState_shouldRespondWithCurrentState() {
        startService()

        onIdle()

        receivedStates.clear()
        // Trigger get-state
        val getStateIntent = Intent(StreamService.ACTION_GET_STATE).setPackage(context.packageName)
        context.sendBroadcast(getStateIntent)


        assertThat(receivedStates).containsExactly(StreamService.StreamStates.PLAYING)
    }

    private fun startService() {
        val startIntent = Intent(context, StreamService::class.java).apply {
            action = StreamService.ACTION_START
            putExtra(StreamService.EXTRA_LINK, radioStation.streamLink)
            putExtra(StreamService.EXTRA_LOGO, radioStation.logoResource)
            putExtra(StreamService.EXTRA_STATION_NAME, radioStation.stationName)
        }
        context.startService(startIntent)
    }
}
