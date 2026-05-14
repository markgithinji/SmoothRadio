package com.smoothradio.radio.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class StreamServiceNotificationTest {
    private lateinit var device: UiDevice
    private lateinit var context: Context

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        context = ApplicationProvider.getApplicationContext()
        hiltRule.inject()
    }

    @Test
    fun clickingHopeFmPlay_shouldTriggerNotificationWithStationName() {
        // Dismiss notification dialog if it appears (Android 13+)
        val allowPopup = device.wait(Until.findObject(By.text("Allow")), 3000)
        allowPopup?.click()

        // Step 1: Scroll to "HOPE FM"
        val scrollable = UiScrollable(UiSelector().scrollable(true))
        if (scrollable.exists()) {
            scrollable.scrollTextIntoView("HOPE FM")
        }

        // Step 2: Click the HOPE FM item to start playback
        val hopeFm = device.wait(Until.findObject(By.text("HOPE FM")), 5000)
        assertThat(hopeFm).isNotNull()
        hopeFm.click()

        // Step 3: Open the notification shade
        device.openNotification()
        
        // Step 4: Assert notification title is present
        val notificationTitle = device.wait(
            Until.findObject(By.text("HOPE FM")),
            15000
        )
        assertThat(notificationTitle).isNotNull()

        // Step 5: Assert notification state sequence
        // It should transition to BUFFERING
        val bufferingState = device.wait(
            Until.findObject(By.text("BUFFERING")),
            10000
        )
        assertThat(bufferingState).isNotNull()

        // Finally, it should show PLAYING
        val playingState = device.wait(
            Until.findObject(By.text("PLAYING")),
            10000
        )
        assertThat(playingState).isNotNull()

        // Step 6: Verify notification click reopens the app
        device.pressHome()
        device.openNotification()
        
        val notificationToClick = device.wait(Until.findObject(By.text("HOPE FM")), 5000)
        notificationToClick.click()
        
        // Wait for app to be in foreground and show station name
        val appVisible = device.wait(Until.hasObject(By.text("HOPE FM")), 5000)
        assertThat(appVisible).isTrue()
    }
}
