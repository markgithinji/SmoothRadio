package com.smoothradio.radio.feature.discover.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smoothradio.radio.HiltTestActivity
import com.smoothradio.radio.core.domain.model.StreamStates
import com.smoothradio.radio.core.domain.repository.PlaybackStateRepository
import com.smoothradio.radio.core.domain.repository.RadioRepository
import com.smoothradio.radio.ui.theme.SmoothRadioTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(
    sdk = [34],
    application = HiltTestApplication::class,
    qualifiers = "w480dp-h800dp-xxhdpi"
)
class DiscoverScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    @Inject
    lateinit var radioRepository: RadioRepository

    @Inject
    lateinit var playbackStateRepository: PlaybackStateRepository

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun discoverScreen_displaysExpectedCategoriesAndStations() = runTest(UnconfinedTestDispatcher()) {
        composeTestRule.setContent {
            SmoothRadioTheme {
                val discoverScrollState = remember { LazyListState() }
                val categoryScrollStates = remember { mutableStateMapOf<String, LazyListState>() }
                DiscoverScreen(
                    discoverScrollState = discoverScrollState,
                    categoryScrollStates = categoryScrollStates,
                )
            }
        }

        composeTestRule.waitForIdle()

        // Wait for content to load and Crossfade to complete
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("discover_content").fetchSemanticsNodes().isNotEmpty()
        }

        // Check categories are present (exists in hierarchy)
        composeTestRule.onNodeWithText("HOT & TRENDING").assertExists()
        composeTestRule.onNodeWithText("KIKUYU").assertExists()

        // Check stations are visible
        composeTestRule.onNodeWithText("RADIO 47").assertIsDisplayed()
        composeTestRule.onNodeWithText("INOORO FM").assertIsDisplayed()
    }

    @Test
    fun clickingFavoriteButton_togglesFavoriteStatus() = runTest(UnconfinedTestDispatcher()) {
        composeTestRule.setContent {
            SmoothRadioTheme {
                val discoverScrollState = remember { LazyListState() }
                val categoryScrollStates = remember { mutableStateMapOf<String, LazyListState>() }
                DiscoverScreen(
                    discoverScrollState = discoverScrollState,
                    categoryScrollStates = categoryScrollStates,
                )
            }
        }

        composeTestRule.waitForIdle()

        // Wait for content (RADIO 47 has ID 228)
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("RADIO 47").fetchSemanticsNodes().isNotEmpty()
        }

        val favButtonMatcher = hasContentDescription("Add to favorites") and 
                hasAnyAncestor(hasTestTag("radio_station_228"))
        val unfavButtonMatcher = hasContentDescription("Remove from favorites") and 
                hasAnyAncestor(hasTestTag("radio_station_228"))

        // Initially not favorite, add to favorites
        composeTestRule.onNode(favButtonMatcher).assertIsDisplayed()
        composeTestRule.onNode(favButtonMatcher).performClick()
        
        // Wait for state update and animation
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        // 2. Verify "Your Favorites" category exists and button has changed
        composeTestRule.onNodeWithText("Your Favorites").assertExists()
        
        // Use onAllNodes because the station now exists in two categories (Favorites and original)
        composeTestRule.onAllNodes(unfavButtonMatcher).onFirst().assertIsDisplayed()
        
        // 3. Remove from favorites (click the first instance of the unfavorite button)
        composeTestRule.onAllNodes(unfavButtonMatcher).onFirst().performClick()
        advanceUntilIdle()

        // Wait for animation and state update
        composeTestRule.waitForIdle()

        // 4. Verify the button for RADIO 47 reverts to "Add to favorites"
        composeTestRule.onNode(favButtonMatcher).assertIsDisplayed()
        // Wait for removal animation from Favorites row if necessary
        composeTestRule.waitForIdle()
        
        // Check if there are any unfavorite buttons left (SOUNDCITY RADIO is still a favorite)
        val anyUnfavMatcher = hasContentDescription("Remove from favorites")
        composeTestRule.onAllNodes(anyUnfavMatcher and hasAnyAncestor(hasTestTag("radio_station_1"))).onFirst().assertExists()
        
        // RADIO 47 should not have an unfavorite button anymore
        composeTestRule.onNode(unfavButtonMatcher).assertDoesNotExist()
    }

    @Test
    fun clickStation_shouldShowLoadingIndicatorWhenBuffering() = runTest(UnconfinedTestDispatcher()) {
        composeTestRule.setContent {
            SmoothRadioTheme {
                val discoverScrollState = remember { LazyListState() }
                val categoryScrollStates = remember { mutableStateMapOf<String, LazyListState>() }
                DiscoverScreen(
                    discoverScrollState = discoverScrollState,
                    categoryScrollStates = categoryScrollStates,
                )
            }
        }

        composeTestRule.waitForIdle()

        // Wait for content
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("RADIO 47").fetchSemanticsNodes().isNotEmpty()
        }

        // Initially no loading indicator
        composeTestRule.onNodeWithTag("dot_loading_animation").assertDoesNotExist()

        // Click to play (RADIO 47 has ID 228)
        composeTestRule.onNodeWithTag("radio_station_228").performClick()
        advanceUntilIdle()
        
        // Manually update playback state to BUFFERING for the selected station
        playbackStateRepository.updateState(StreamStates.BUFFERING)
        // Ensure the repo knows which one is "playing"
        radioRepository.setPlayingStation(228)
        
        composeTestRule.waitForIdle()
        advanceUntilIdle()

        // Wait for loading animation to appear
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("dot_loading_animation", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Check for loading animation (using unmerged tree due to clickable parent)
        composeTestRule.onNodeWithTag("dot_loading_animation", useUnmergedTree = true).assertExists()
    }
}
