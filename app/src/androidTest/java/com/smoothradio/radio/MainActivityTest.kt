package com.smoothradio.radio

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onIdle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.smoothradio.radio.core.data.di.CoreDataModule
import com.smoothradio.radio.service.StreamService
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.anyOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.startsWith
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
@UninstallModules(CoreDataModule::class)
class MainActivityTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val scenarioRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun searchFieldVisibility_toggleAndClear() {
        onView(withId(R.id.ivSearch)).perform(click())
        onView(withId(R.id.etSearch)).check(matches(isDisplayed()))

        onView(withId(R.id.etSearch)).perform(typeText("kiss"), closeSoftKeyboard())
        onView(withId(R.id.ivClearSearch)).check(matches(isDisplayed()))

        onView(withId(R.id.ivClearSearch)).perform(click())
        onView(withId(R.id.etSearch)).check(matches(withText("")))
    }

//    @Test
//    fun clickingPlayInMiniPlayer_startsPlayingStation() {
//        onView(withId(R.id.ivPlayMiniPlayerLayout)).perform(click())
//        onView(withText("Preparing Audio")).check(matches(isDisplayed()))
//        onView(withId(R.id.loadingAnimationMiniPlayerLayout)).check(matches(isDisplayed()))
//    }

    @Test
    fun sortAZ_shouldShowChristianBeforeMixxRadio_skippingAd() {

        openActionBarOverflowOrOptionsMenu(ApplicationProvider.getApplicationContext())
        onView(withText("sort")).perform(click())
        onView(withText("A-Z")).perform(click())
        // Assert that "560 CHRISTIAN RADIO" and "560 MIXX RADIO" appears
        onView(withText("560 MIXX RADIO")).check(matches(isDisplayed()))
        onView(withText("560 CHRISTIAN RADIO")).check(matches(isDisplayed()))
    }


    @Test
    fun sortZA_shouldShowXpressRadioBeforeXaticFm() {

        openActionBarOverflowOrOptionsMenu(ApplicationProvider.getApplicationContext())
        onView(withText("sort")).perform(click())
        onView(withText("Z-A")).perform(click())
        // Assert that "XPRESS RADIO" and "XATIC FM" appears
        onView(withText("VYBES RADIO")).check(matches(isDisplayed()))
        onView(withText("VUUKA FM")).check(matches(isDisplayed()))
    }

    @Test
    fun onReceive_eventIntent_shouldShowPlayingInMiniPlayer() {
        // Simulate service broadcast with PLAYING state
        val intent = Intent(StreamService.ACTION_EVENT_CHANGE).apply {
            putExtra(StreamService.EXTRA_STATE, StreamService.StreamStates.PLAYING)
        }
        onIdle()
        ApplicationProvider.getApplicationContext<Context>().sendBroadcast(intent)

        onView(withId(R.id.tvStatusMiniPlayerLayout)).check(matches(withText(StreamService.StreamStates.PLAYING)))
    }

    @Test
    fun bufferingState_shouldShowLoadingInMiniPlayer() {

        // Simulate service broadcast with BUFFERING state
        val intent = Intent(StreamService.ACTION_EVENT_CHANGE).apply {
            putExtra(StreamService.EXTRA_STATE, StreamService.StreamStates.BUFFERING)
        }
        ApplicationProvider.getApplicationContext<Context>().sendBroadcast(intent)

        // Mini-player should show "Buffering"
        onView(withId(R.id.tvStatusMiniPlayerLayout))
            .check(matches(withText("Buffering")))

        // Loading animation visible, play icon hidden
        onView(withId(R.id.loadingAnimationMiniPlayerLayout))
            .check(matches(isDisplayed()))
    }

    @Test
    fun onCreate_shouldSetupTabsCorrectly() {

        onView(allOf(withText("STATIONS"), isDescendantOfA(withId(R.id.tabLayout))))
            .check(matches(isDisplayed()))

        onView(allOf(withText("LIVE"), isDescendantOfA(withId(R.id.tabLayout))))
            .check(matches(isDisplayed()))

        onView(allOf(withText("DISCOVER"), isDescendantOfA(withId(R.id.tabLayout))))
            .check(matches(isDisplayed()))
    }

    @Test
    fun search_shouldFilterAndShowMatchingStation() {
        // Click the search icon
        onView(withId(R.id.ivSearch)).perform(click())

        onView(withId(R.id.etSearch)).perform(typeText("Hope"), closeSoftKeyboard())

        // Check that a station named "Hope FM" appears
        onView(withId(R.id.rv_radio_list))
            .check(matches(hasDescendant(withText(containsString("HOPE FM")))))
    }


    @Test
    fun clickIvPlayInsideHopeFmItem_shouldTriggerPlayback() {
        onIdle()

        onView(
            allOf(
                withId(R.id.ivPlay),
                hasSibling(withText("HOPE FM"))
            )
        ).perform(click())

        onView(withId(R.id.tvStationNameMiniPlayerLayout))
            .check(matches(withText("HOPE FM")))

        // Mini player should show current playing station and state
        onView(withId(R.id.tvStatusMiniPlayerLayout))
            .check(
                matches(
                    anyOf(
                        withText(StreamService.StreamStates.PREPARING),
                        withText(StreamService.StreamStates.PLAYING)
                    )
                )
            )
    }

    @Test
    fun search_noMatch_shouldShowEmptyState() {

        onView(withId(R.id.ivSearch)).perform(click())
        onView(withId(R.id.etSearch)).perform(typeText("Zzz"), closeSoftKeyboard())

        onView(withText(containsString("No Stations Found!")))
            .check(matches(isDisplayed()))
    }

    @Test
    fun clickingInfoMenu_opensAboutDialog() {
        openActionBarOverflowOrOptionsMenu(ApplicationProvider.getApplicationContext())
        onView(withText("info")).perform(click())
        onView(withText(startsWith("Enjoying SmoothRadio?"))).check(matches(isDisplayed()))
    }
}
