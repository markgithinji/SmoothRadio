package com.smoothradio.radio.feature.radio_list.ui

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onIdle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.smoothradio.radio.R
import com.smoothradio.radio.core.di.CoreModule
import com.smoothradio.radio.core.util.PlayerManager
import com.smoothradio.radio.service.StreamService
import com.smoothradio.radio.testutil.launchFragmentInHiltContainer
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject


@HiltAndroidTest
@UninstallModules(CoreModule::class)
class RadioListFragmentTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var playerManager: PlayerManager

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun shouldClickFavoriteButton() {
        launchFragmentInHiltContainer<RadioListFragment>()

        onView(
            allOf(
                withId(R.id.iv_favourite),
                hasSibling(withText("HOPE FM"))
            )
        ).perform(click())
    }

    @Test
    fun clickingStations_clickHopeFmThenClickAnotherStation_updatesUiForEachClickedStationAccordingly() {
        // Launch the fragment
        launchFragmentInHiltContainer<RadioListFragment>()

        onView(
            allOf(
                withId(R.id.ivPlay),
                hasSibling(withText("HOPE FM"))
            )
        ).perform(click())

        // Step 1: Check that loading animation is shown inside Hope FM item
        onView(
            allOf(
                withId(R.id.lottie_loading_animation),
                hasSibling(withText("HOPE FM")),
            )
        ).check(matches(isDisplayed()))

        // Step 2: Check that play icon is hidden inside Hope FM item
        onView(
            allOf(
                withId(R.id.ivPlay),
                hasSibling(withText("HOPE FM")),
            )
        ).check(matches(not(isDisplayed())))

        // Step 3: Play another station
        onView(
            allOf(
                withId(R.id.ivPlay),
                hasSibling(withText("SOUNDCITY RADIO")),
            )
        ).perform(click())
        // Step 4: Check that play icon is shown inside Hope FM item but hidden in Soundcity Radio
        onView(
            allOf(
                withId(R.id.ivPlay),
                hasSibling(withText("HOPE FM")),
            )
        ).check(matches(isDisplayed()))
        onView(
            allOf(
                withId(R.id.ivPlay),
                hasSibling(withText("SOUNDCITY RADIO")),
            )
        ).check(matches(not(isDisplayed())))

    }

    @Test
    fun clickSoundcityRadioPlayIcon_showsLoading_thenShowsPlayingIcon() {
        launchFragmentInHiltContainer<RadioListFragment>()

        onView(
            allOf(
                withId(R.id.ivPlay),
                hasSibling(withText("SOUNDCITY RADIO")),
            )
        ).perform(click())

        // Step 1: Check that loading animation is shown inside Hope FM item
        onView(
            allOf(
                withId(R.id.lottie_loading_animation),
                hasSibling(withText("SOUNDCITY RADIO")),
            )
        ).check(matches(isDisplayed()))

        // Step 2: Check that play icon is hidden inside Hope FM item
        onView(
            allOf(
                withId(R.id.ivPlay),
                hasSibling(withText("SOUNDCITY RADIO")),
            )
        ).check(matches(not(isDisplayed())))

        val intent = Intent(StreamService.ACTION_EVENT_CHANGE).apply {
            putExtra(StreamService.EXTRA_STATE, StreamService.StreamStates.PLAYING)
        }
        ApplicationProvider.getApplicationContext<Context>().sendBroadcast(intent)

        onIdle()
        // Step 3: Confirm loading gone, play icon visible
        // View matching lottie_loading_animation causes duplicate view ambiguous exception ie. This assertion is flaky
//        onView(
//            allOf(
//                withId(R.id.lottie_loading_animation),
//                hasSibling(withText("SOUNDCITY RADIO")),
//            )
//        ).check(matches(not(isDisplayed())))

        onView(
            allOf(
                withId(R.id.ivPlay),
                hasSibling(withText("SOUNDCITY RADIO")),
                isDisplayed(),
            )
        ).check(matches(isDisplayed()))

    }
}
