package com.smoothradio.radio.core.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onIdle
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.HiltTestActivity
import com.smoothradio.radio.R
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.service.StreamService
import com.smoothradio.radio.testutil.SimpleFlagIdlingResource
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PlayerManagerIntegrationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val activityRule = ActivityScenarioRule(HiltTestActivity::class.java)

    @Inject
    lateinit var playerManager: PlayerManager

    private val context: Context = ApplicationProvider.getApplicationContext()

    private var playbackCompleted = false
    private val idlingResource = SimpleFlagIdlingResource { playbackCompleted }

    @Before
    fun setup() {
        hiltRule.inject()
        // IdlingResource that waits until playbackCompleted becomes true
        Espresso.registerIdlingResources(idlingResource)
    }

    @After
    fun tearDown() {
        playerManager.unbindActivity()
        Espresso.unregisterIdlingResources(idlingResource)
    }


    @Test
    fun refresh_shouldSetIsShowingAdTrueImmediately() {
        activityRule.scenario.onActivity { activity ->
            playerManager.bindActivity(activity)
            playerManager.setRadioStation(
                RadioStation(2, 0, "Ad Refresh", "88.8", "City", "http://stream", false, false, 0)
            )

            playerManager.refresh()

            assertThat(playerManager.isShowingAd).isTrue()
        }
    }

    @Test
    fun playLifecyle_shouldEmitPreparingThenPlayingThenIdle() {
        val receivedStates = mutableListOf<String>()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.getStringExtra(StreamService.EXTRA_STATE)?.let { state ->
                    receivedStates.add(state)
                    if (state == StreamService.StreamStates.PLAYING) {
                        playerManager.playOrStop()
                        playbackCompleted = true
                    }
                }
            }
        }

        context.registerReceiver(
            receiver,
            IntentFilter(StreamService.ACTION_EVENT_CHANGE),
            Context.RECEIVER_NOT_EXPORTED
        )

        activityRule.scenario.onActivity { activity ->
            playerManager.bindActivity(activity)
            playerManager.setRadioStation(
                RadioStation(
                    id = 1,
                    logoResource = R.drawable.hopefm,
                    stationName = "Hope FM",
                    frequency = "101.1",
                    location = "Nairobi",
                    streamLink = "https://a5.asurahosting.com:7530/radio.mp3",
                    isPlaying = false,
                    isFavorite = false,
                    orderIndex = 0
                )
            )
            playerManager.playOrStop()
        }

        // Wait until IDLE state is received before asserting
        onIdle()

        assertThat(receivedStates)
            .containsAtLeast(
                StreamService.StreamStates.PREPARING,
                StreamService.StreamStates.PLAYING,
                StreamService.StreamStates.IDLE
            )
            .inOrder()

        context.unregisterReceiver(receiver)
    }
}
