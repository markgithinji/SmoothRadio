package com.smoothradio.radio.feature.player.util

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smoothradio.radio.R
import com.smoothradio.radio.feature.player.ui.PlayerFragment
import com.smoothradio.radio.testutil.launchFragmentInHiltContainer
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TimerSetterHelperEspressoTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun clickingSetTimerButton_shouldShowTimePickerDialog() {
        launchFragmentInHiltContainer<PlayerFragment>()

        // Click the timer icon
        onView(withId(R.id.ivSetTimer)).perform(click())

        // Check if the time picker dialog is shown
        onView(withText("Set Time To Turn Off Radio"))
            .check(matches(isDisplayed()))
    }
}


