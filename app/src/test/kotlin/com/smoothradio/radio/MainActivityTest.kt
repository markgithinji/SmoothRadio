package com.smoothradio.radio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(
    sdk = [34],
    application = HiltTestApplication::class,
    qualifiers = "w480dp-h800dp-xxhdpi"
)
class MainActivityTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    private fun dismissChangelogIfVisible() {
        // Use a small wait in case it's still appearing due to the 2s delay
        composeTestRule.onAllNodesWithText("Got it!").fetchSemanticsNodes().let {
            if (it.isNotEmpty()) {
                composeTestRule.onNodeWithText("Got it!").performClick()
                composeTestRule.waitForIdle()
            }
        }
    }

    @Test
    fun bottomNavigation_hasThreeTabs() = runTest {
        composeTestRule.waitForIdle()
        dismissChangelogIfVisible()
        
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Stations").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Stations").assertIsDisplayed()
        composeTestRule.onNodeWithText("Live").assertIsDisplayed()
        composeTestRule.onNodeWithText("Discover").assertIsDisplayed()
    }

    @Test
    fun clickingLiveTab_showsPlayerScreen_withDefaultStation() = runTest {
        composeTestRule.waitForIdle()
        dismissChangelogIfVisible()

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
    fun clickingDiscoverTab_showsDiscoverScreen() = runTest {
        composeTestRule.waitForIdle()
        dismissChangelogIfVisible()

        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Discover").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Discover").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("DISCOVER", substring = true).assertIsDisplayed()
    }
}
