package com.smoothradio.radio.feature.discover.ui

import androidx.test.espresso.Espresso.onIdle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.smoothradio.radio.R
import com.smoothradio.radio.testutil.launchFragmentInHiltContainer
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@LargeTest
@RunWith(AndroidJUnit4::class)
class DiscoverFragmentHiltTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun shouldClickFavoriteButton() {
        launchFragmentInHiltContainer<DiscoverFragment> { }

        onView(
            allOf(
                withId(R.id.ivCategoryFavourite),
                hasSibling(withText("Hope FM"))
            )
        ).perform(click())
    }

    @Test
    fun clickPlayButton_shouldUpdateUIForClickedStationAccordingly() {
        launchFragmentInHiltContainer<DiscoverFragment> { }

        onView(
            allOf(
                withId(R.id.ivCategoryPlay),
                hasSibling(withText("Hope FM"))
            )
        ).perform(click())

        // Step 1: Check that loading animation is shown inside Hope FM item
        onView(
            allOf(
                withId(R.id.lottie_loading_animation),
                hasSibling(withText("Hope FM")),
            )
        ).check(matches(isDisplayed()))

        // Step 2: Check that play icon is hidden inside Hope FM item
        onView(
            allOf(
                withId(R.id.ivCategoryPlay),
                hasSibling(withText("Hope FM")),
            )
        ).check(matches(not(isDisplayed())))

//        // Step 3: Play another station
//        onView(
//            allOf(
//                withId(R.id.ivCategoryPlay),
//                hasSibling(withText("Inooro FM")),
//            )
//        ).perform(click())
//        // Step 4: Check that play icon is shown inside Hope FM item but hidden in Inooro Radio
//        Thread.sleep(3000)
//        onView(
//            allOf(
//                withId(R.id.ivCategoryPlay),
//                hasSibling(withText("Hope FM")),
//            )
//        ).check(matches(isDisplayed()))
//        onView(
//            allOf(
//                withId(R.id.ivCategoryPlay),
//                hasSibling(withText("Inooro FM")),
//            )
//        ).check(matches(not(isDisplayed())))
    }


    @Test
    fun discoverFragment_shouldDisplayExpectedCategories() {
        launchFragmentInHiltContainer<DiscoverFragment> {
        }

        // Check categories are visible
        onView(withText("HOT & TRENDING")).check(matches(isDisplayed()))
        onView(withText("LIVE MIXXES")).check(matches(isDisplayed()))
        onView(withText("KIKUYU")).check(matches(isDisplayed()))

        // Check that known stations (by name) appear
        onView(withText("Hope FM")).check(matches(isDisplayed())) // Hope FM with id=1, should show up in HOT & TRENDING
        onView(withText("Inooro FM")).check(matches(isDisplayed()))  // Inooro FM with id=129, should show up in KIKUYU
    }

}
