package com.smoothradio.radio

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun bottomNavigation_hasThreeTabs() {
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Stations").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Stations").assertIsDisplayed()
        composeTestRule.onNodeWithText("Live").assertIsDisplayed()
        composeTestRule.onNodeWithText("Discover").assertIsDisplayed()
    }

    @Test
    fun clickingLiveTab_showsPlayerScreen_withDefaultStation() {
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Live").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Live").performClick()
        composeTestRule.waitForIdle()

        // Player screen shows HOPE FM (the default station)
        composeTestRule.onNodeWithText("HOPE FM").assertIsDisplayed()

        // Play button should be visible
        composeTestRule.onNodeWithContentDescription("Play").assertIsDisplayed()
    }

    @Test
    fun clickingDiscoverTab_showsDiscoverScreen() {
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Discover").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Discover").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("DISCOVER", substring = true).assertIsDisplayed()
    }
}