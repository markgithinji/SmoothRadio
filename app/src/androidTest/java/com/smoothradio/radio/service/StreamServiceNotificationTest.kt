package com.smoothradio.radio.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
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
    private val packageName: String =
        ApplicationProvider.getApplicationContext<Context>().packageName


    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        context = ApplicationProvider.getApplicationContext()
        hiltRule.inject()
    }

    @Test
    fun clickingHopeFmPlay_shouldTriggerNotificationWithStationName() {
        // Step 1: Scroll to "Hope FM"
        val scrollable = UiScrollable(UiSelector().scrollable(true))
        scrollable.scrollTextIntoView("HOPE FM")

        // Step 2: Click ivPlay inside Hope FM item
        val ivPlay = device.findObject(
            By.res(packageName, "ivPlay").hasAncestor(By.text("HOPE FM"))
        )
        checkNotNull(ivPlay) { "ivPlay not found for Hope FM" }
        ivPlay.click()

        // Step 3: Wait for StreamService to start
        device.wait(Until.hasObject(By.pkg(packageName)), 5_000)

        // Step 4: Open the notification shade
        device.openNotification()
        device.wait(Until.hasObject(By.text("HOPE FM")), 5_000)

        // Step 5: Assert notification title is present
        val notification = device.findObject(By.text("HOPE FM"))
        assertThat(notification).isNotNull()
    }
}
