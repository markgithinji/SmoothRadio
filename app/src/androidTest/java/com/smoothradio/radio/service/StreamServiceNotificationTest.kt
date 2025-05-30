package com.smoothradio.radio.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
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
    private val packageName: String =
        ApplicationProvider.getApplicationContext<Context>().packageName

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
        // Dismiss notification dialog
        val allowPopup = device.wait(Until.findObject(By.text("Allow")), 3000)
        allowPopup?.click()
        // Step 1: Scroll to "Hope FM"
        val scrollable = UiScrollable(UiSelector().scrollable(true))
        scrollable.scrollTextIntoView("HOPE FM")

        // Step 2: Click ivPlay inside Hope FM item
        val ivPlay = device.findObject(
            By.res(packageName, "ivPlay")
                .hasAncestor(
                    By.res(packageName, "rv_radio_list")
                        .hasDescendant(By.text("HOPE FM"))
                )
        )
        assertThat(ivPlay).isNotNull()
        ivPlay.click()

        // Step 3: Open the notification shade
        device.openNotification()
        device.wait(Until.hasObject(By.text("HOPE FM")), 5_000)

        // Step 4: Assert notification title is present
        val notification = device.wait(
            Until.findObject(By.textContains("HOPE FM")),
            5000
        )
        assertThat(notification).isNotNull()
        // Step 5: Assert notification playing state is present
        val notificationState = device.wait(
            Until.findObject(By.textContains("Preparing Audio")),
            5000
        )
        assertThat(notificationState).isNotNull()
        // Step 6: Notification click should reopen the app
        device.pressHome()
        device.openNotification()
        device.wait(Until.hasObject(By.text("HOPE FM")), 5_000)
        notificationState.click()
        device.wait(Until.hasObject(By.text("HOPE FM")), 5_000)

    }
}
