package com.smoothradio.radio.feature.player.ui

import android.content.Context
import android.content.Intent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.smoothradio.radio.R
import com.smoothradio.radio.service.StreamService
import com.smoothradio.radio.testutil.launchFragmentInHiltContainer
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@LargeTest
@RunWith(AndroidJUnit4::class)
class PlayerFragmentTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private lateinit var context: Context

    @Before
    fun setup() {
        hiltRule.inject()
        context =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun shouldDisplayStationLogoAndName_whenStationIsAvailable() {
        launchFragmentInHiltContainer<PlayerFragment> {}

        onView(withId(R.id.ivLargeLogo))
            .check(matches(isDisplayed()))

        onView(withId(R.id.tv_station_name_player_frag))
            .check(matches(withText("")))
    }

    @Test
    fun shouldShowPauseIcon_whenPlaying() {
        launchFragmentInHiltContainer<PlayerFragment> {}

        sendStreamState(StreamService.StreamStates.PLAYING)

        onView(withId(R.id.ivPlayButton))
            .check(matches(isDisplayed()))
    }

    @Test
    fun shouldShowLoadingAnimation_whenBuffering() {
        launchFragmentInHiltContainer<PlayerFragment> {}

        sendStreamState(StreamService.StreamStates.BUFFERING)

        onView(withId(R.id.lottie_loading_animation))
            .check(matches(isDisplayed()))
    }


    @Test
    fun shouldShowEqualizerAnimation_whenStateIsPlaying() {
        launchFragmentInHiltContainer<PlayerFragment> {}

        sendStreamState(StreamService.StreamStates.PLAYING)

        onView(withId(R.id.equalizerAnimation))
            .check(matches(isDisplayed()))
    }

    @Test
    fun shouldUpdateMetadataText_whenMetadataBroadcastReceived() {
        launchFragmentInHiltContainer<PlayerFragment> {}

        val intent = Intent(StreamService.ACTION_METADATA_CHANGE).apply {
            setPackage(context.packageName)
            putExtra(StreamService.EXTRA_TITLE, "Now Playing: Gospel Mix")
        }
        context.sendBroadcast(intent)

        onView(withId(R.id.tvMetadata))
            .check(matches(withText("Now Playing: Gospel Mix")))
    }

    @Test
    fun shouldClickRefreshButton() {
        launchFragmentInHiltContainer<PlayerFragment> {}

        onView(withId(R.id.ivRefresh))
            .check(matches(isDisplayed()))
            .perform(click())
    }

    @Test
    fun shouldClickPlayButton() {
        launchFragmentInHiltContainer<PlayerFragment> {}

        onView(withId(R.id.ivPlayButton))
            .check(matches(isDisplayed()))
            .perform(click())
    }

    private fun sendStreamState(state: String) {
        val intent = Intent(StreamService.ACTION_EVENT_CHANGE).apply {
            setPackage(context.packageName)
            putExtra(StreamService.EXTRA_STATE, state)
        }
        context.sendBroadcast(intent)
    }
}
