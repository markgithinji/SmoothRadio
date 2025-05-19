package com.smoothradio.radio.core.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.room.concurrent.AtomicBoolean
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onIdle
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.HiltTestActivity
import com.smoothradio.radio.R
import com.smoothradio.radio.core.model.RadioStation
import com.smoothradio.radio.service.StreamService
import com.smoothradio.radio.testutil.PlayerIdleResource
import com.smoothradio.radio.testutil.PlayerStateTracker
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PlayerManagerIntegrationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val activityRule = ActivityScenarioRule(HiltTestActivity::class.java)

    @Before
    fun setup() {
        hiltRule.inject()
    }


    @Test
    fun playLifecycle_shouldEmitPreparingThenPlayingThenEnded() {
        val receivedStates = mutableListOf<String>()
        val context = ApplicationProvider.getApplicationContext<Context>()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val state = intent?.getStringExtra(StreamService.EXTRA_STATE)
                if (state != null) {
                    receivedStates.add(state)
                    PlayerStateTracker.updateState(state)
                }
            }
        }

        context.registerReceiver(
            receiver,
            IntentFilter(StreamService.ACTION_EVENT_CHANGE),
            Context.RECEIVER_NOT_EXPORTED
        )

        // Track PLAYING
        val playingIdle = PlayerIdleResource(setOf(StreamService.StreamStates.PLAYING))
        IdlingRegistry.getInstance().register(playingIdle)

        activityRule.scenario.onActivity { activity ->
            val manager = PlayerManager(activity)
            manager.setRadioStation(
                RadioStation(
                    id = 1,
                    logoResource = R.drawable.hopefm,
                    stationName = "Hope FM",
                    frequency = "101.1",
                    location = "Nairobi",
                    streamLink = "https://a5.asurahosting.com:7530/radio.mp3",
                    isPlaying = false,
                    isFavorite = false
                )
            )
            manager.playOrStop()
        }

        // Wait for PLAYING
        onIdle()

        IdlingRegistry.getInstance().unregister(playingIdle)
        PlayerStateTracker.clearListener()

        // Now track IDLE (END)
        val idleIdle = PlayerIdleResource(setOf(StreamService.StreamStates.IDLE))
        IdlingRegistry.getInstance().register(idleIdle)

        activityRule.scenario.onActivity { activity ->
            val manager = PlayerManager(activity)
            manager.playOrStop() // stop it
        }

        // Wait for IDLE
        onIdle()

        IdlingRegistry.getInstance().unregister(idleIdle)
        PlayerStateTracker.clearListener()
        context.unregisterReceiver(receiver)

        assertThat(receivedStates).containsAtLeast(
            StreamService.StreamStates.PREPARING,
            StreamService.StreamStates.PLAYING,
            StreamService.StreamStates.IDLE
        )
    }


    @Test
    fun playOrStop_shouldSetIsShowingAdTrueImmediately() {
        activityRule.scenario.onActivity { activity ->
            val playerManager = PlayerManager(activity)
            playerManager.setRadioStation(
                RadioStation(1, 0, "Ad FM", "99.9", "City", "http://stream", false, false)
            )

            playerManager.playOrStop()

            // Assert immediately
            assertThat(playerManager.isShowingAd).isTrue()
        }
    }

    @Test
    fun refresh_shouldSetIsShowingAdTrueImmediately() {
        activityRule.scenario.onActivity { activity ->
            val playerManager = PlayerManager(activity)
            playerManager.setRadioStation(
                RadioStation(2, 0, "Ad Refresh", "88.8", "City", "http://stream", false, false)
            )

            playerManager.refresh()

            assertThat(playerManager.isShowingAd).isTrue()
        }
    }

    @Test
    fun playLifecycle_shouldEmitPreparingThenPlayingThenIdle_withIdlingResource() {
        val receivedStates = mutableListOf<String>()
        val context = ApplicationProvider.getApplicationContext<Context>()
        var playerManagerIdlingResource: PlayerManagerIdlingResource? = null

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val state = intent?.getStringExtra(StreamService.EXTRA_STATE)
                if (state != null) {
                    receivedStates.add(state)
                    playerManagerIdlingResource?.onStateChanged(state)
                }
            }
        }

        context.registerReceiver(
            receiver,
            IntentFilter(StreamService.ACTION_EVENT_CHANGE),
            Context.RECEIVER_NOT_EXPORTED
        )

        activityRule.scenario.onActivity { activity ->
            val manager = PlayerManager(activity)
            val idlingResource = PlayerManagerIdlingResource(
                manager,
                setOf(StreamService.StreamStates.PLAYING, StreamService.StreamStates.IDLE)
            )
            Espresso.registerIdlingResources(idlingResource)
            playerManagerIdlingResource = idlingResource

            manager.setRadioStation(
                RadioStation(
                    id = 1,
                    logoResource = R.drawable.hopefm,
                    stationName = "Hope FM",
                    frequency = "101.1",
                    location = "Nairobi",
                    streamLink = "https://a5.asurahosting.com:7530/radio.mp3", // real streaming URL
                    isPlaying = false,
                    isFavorite = false
                )
            )

            manager.playOrStop() // Start playing

            // Now we don't use delay. We trigger the stop action and rely on
            // the IdlingResource to tell Espresso when to proceed with assertions.
            CoroutineScope(Dispatchers.Main).launch {
                // We need to ensure Espresso waits for the PLAYING state
                // before we trigger the stop. The IdlingResource should handle this.
                // Let's trigger the stop after a short, non-blocking yield,
                // assuming the IdlingResource will manage the waiting.
                yield()
                manager.playOrStop() // Stop playback
            }

            try {
                // Espresso will wait until the IdlingResource reports idle
                // before these assertions are run.
                assertThat(receivedStates).contains(StreamService.StreamStates.PREPARING)
                assertThat(receivedStates).contains(StreamService.StreamStates.PLAYING)
                assertThat(receivedStates).contains(StreamService.StreamStates.IDLE)
            } finally {
                Espresso.unregisterIdlingResources(idlingResource)
                context.unregisterReceiver(receiver)
            }
        }
    }

    class PlayerManagerIdlingResource(
        private val playerManager: PlayerManager,
        private val targetStates: Set<String>
    ) : IdlingResource {

        @Volatile
        private var resourceCallback: IdlingResource.ResourceCallback? = null
        private val isIdle = AtomicBoolean(false)
        private var currentState: String? = null

        fun onStateChanged(newState: String) {
            currentState = newState
            val nowIdle = targetStates.contains(newState)
            if (isIdle.compareAndSet(!nowIdle, nowIdle) && nowIdle) {
                resourceCallback?.onTransitionToIdle()
            } else if (!nowIdle) {
                isIdle.set(false) // Ensure it's marked as busy when in a non-target state
            }
        }

        override fun getName(): String {
            return "PlayerManager state: $targetStates"
        }

        override fun isIdleNow(): Boolean {
            return isIdle.get()
        }

        override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback?) {
            this.resourceCallback = callback
        }
    }


}
