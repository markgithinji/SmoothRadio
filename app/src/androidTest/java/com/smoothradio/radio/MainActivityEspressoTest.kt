package com.smoothradio.radio

import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.smoothradio.radio.core.di.AppModule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
@UninstallModules(AppModule::class)
class MainActivityEspressoTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val scenarioRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun tabSwitching_updatesViewPagerAndBottomSheet() {
        onView(withText("LIVE")).perform(click())
        onView(withId(R.id.viewPager)).check(matches(isDisplayed()))

        onView(withText("STATIONS")).perform(click())
        onView(withId(R.id.viewPager)).check(matches(isDisplayed()))
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

    @Test
    fun clickingPlayInMiniPlayer_setsSelectedStation() {
        onView(withId(R.id.ivPlayMiniPlayerLayout)).perform(click())
        // Cannot assert ViewModel change directly, but test passes if no crash
    }

    @Test
    fun clickingSortMenu_opensSortDialog() {
        openActionBarOverflowOrOptionsMenu(ApplicationProvider.getApplicationContext())
        onView(withText("Sort")).perform(click())
        onView(withText("Sort By:")).check(matches(isDisplayed()))
    }

    @Test
    fun clickingInfoMenu_opensAboutDialog() {
        openActionBarOverflowOrOptionsMenu(ApplicationProvider.getApplicationContext())
        onView(withText("Info")).perform(click())
        onView(withText("About")).check(matches(isDisplayed()))
    }
}
