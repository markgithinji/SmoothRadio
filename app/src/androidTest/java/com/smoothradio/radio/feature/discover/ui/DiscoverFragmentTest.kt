package com.smoothradio.radio.feature.discover.ui

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.smoothradio.radio.R
import com.smoothradio.radio.testutil.launchFragmentInHiltContainer
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.TypeSafeMatcher
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
    fun shouldDisplayDiscoverRecyclerView() {
        launchFragmentInHiltContainer<DiscoverFragment> { }

        onView(withId(R.id.rvDiscover)).check(matches(isDisplayed()))
    }

    @Test
    fun shouldClickFavoriteButton() {
        launchFragmentInHiltContainer<DiscoverFragment> { }

        onView(
            allOf(
                withId(R.id.ivCategoryFavourite),
                withTextInRecyclerItem("Hope FM", R.id.ivCategoryFavourite)
            )
        )
            .perform(click())
    }

    @Test
    fun shouldClickPlayButton() {
        launchFragmentInHiltContainer<DiscoverFragment> { }

        onView(
            allOf(
                withId(R.id.ivCategoryPlay),
                withTextInRecyclerItem("Hope FM", R.id.ivCategoryPlay)
            )
        )
            .perform(click())
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
        onView(withText("Hope FM")).check(matches(isDisplayed()))          // id=1, HOT & TRENDING // id=129, LIVE MIXXES
        onView(withText("Inooro FM")).check(matches(isDisplayed()))
    }

    private fun withTextInRecyclerItem(stationName: String, @IdRes targetViewId: Int): Matcher<View> {
        return object : TypeSafeMatcher<View>() {
            override fun describeTo(description: Description) {
                description.appendText("View with ID $targetViewId in item containing text: $stationName")
            }

            public override fun matchesSafely(view: View): Boolean {
                val parent = view.parent
                if (parent !is View) return false

                val hasTextView =
                    (parent as ViewGroup).findViewById<View>(R.id.tvCategoryChannelName)?.let {
                        it is TextView && it.text == stationName
                    } ?: false

                return hasTextView && view.id == targetViewId
            }
        }
    }

}
