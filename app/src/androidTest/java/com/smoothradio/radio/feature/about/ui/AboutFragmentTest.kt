package com.smoothradio.radio.feature.about.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smoothradio.radio.R
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutFragmentTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        Intents.init()
        launchFragmentInContainer<AboutFragment>(themeResId = R.style.Theme_MyApplication)
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun clickingFbAddress_shouldOpenFacebookPage() {
        val expectedUri = Uri.parse(context.getString(R.string.facebook_url))

        onView(withId(R.id.fbAddress)).perform(click())

        Intents.intended(
            allOf(
                hasAction(Intent.ACTION_VIEW),
                hasData(expectedUri)
            )
        )
    }

    @Test
    fun clickingTvEmail_shouldLaunchEmailChooser() {
        onView(withId(R.id.tvEmail)).perform(click())

        Intents.intended(hasAction(Intent.ACTION_CHOOSER))
    }

    @Test
    fun infoText_shouldDisplayCorrectText() {
        val expectedText = context.getString(R.string.info_text)

        onView(withId(R.id.tvInfo)).check(matches(withText(expectedText)))
    }
}
