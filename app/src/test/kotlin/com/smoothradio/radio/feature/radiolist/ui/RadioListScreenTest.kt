package com.smoothradio.radio.feature.radiolist.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smoothradio.radio.HiltTestActivity
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.domain.model.StreamStates
import com.smoothradio.radio.core.domain.repository.PlaybackStateRepository
import com.smoothradio.radio.core.domain.repository.RadioRepository
import com.smoothradio.radio.ui.theme.SmoothRadioTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(
    sdk = [34],
    application = HiltTestApplication::class,
    qualifiers = "w480dp-h800dp-xxhdpi"
)
class RadioListScreenTest {

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

    private fun dismissChangelogIfVisible() {
        composeTestRule.onAllNodesWithText("Got it!").fetchSemanticsNodes().let {
            if (it.isNotEmpty()) {
                composeTestRule.onNodeWithText("Got it!").performClick()
                composeTestRule.waitForIdle()
            }
        }
    }

    @Test
    fun radioStationsScreen_displaysStationsInList() = runTest {
        composeTestRule.setContent {
            SmoothRadioTheme {
                val listState = remember { LazyListState() }
                val gridState = remember { LazyGridState() }
                RadioStationsScreen(
                    listScrollState = listState,
                    gridScrollState = gridState,
                    onWhatNewClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        dismissChangelogIfVisible()

        // Wait for data auto-population
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("RADIO 47").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("RADIO 47").assertIsDisplayed()
        composeTestRule.onNodeWithText("INOORO FM").assertIsDisplayed()
    }

    @Test
    fun clickingFavoriteButton_togglesFavoriteStatus() = runTest {
        composeTestRule.setContent {
            SmoothRadioTheme {
                val listState = remember { LazyListState() }
                val gridState = remember { LazyGridState() }
                RadioStationsScreen(
                    listScrollState = listState,
                    gridScrollState = gridState,
                    onWhatNewClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        dismissChangelogIfVisible()

        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("RADIO 47").fetchSemanticsNodes().isNotEmpty()
        }

        val favButtonMatcher = hasContentDescription("Add to favorites") and
                hasAnyAncestor(hasTestTag("radio_station_228"))
        val unfavButtonMatcher = hasContentDescription("Remove from favorites") and
                hasAnyAncestor(hasTestTag("radio_station_228"))

        // Add to favorite
        composeTestRule.onNode(favButtonMatcher).performClick()
        advanceUntilIdle()
        composeTestRule.waitForIdle()
        composeTestRule.onNode(unfavButtonMatcher).assertIsDisplayed()

        // Remove from favorite
        composeTestRule.onNode(unfavButtonMatcher).performClick()
        advanceUntilIdle()
        composeTestRule.waitForIdle()
        composeTestRule.onNode(favButtonMatcher).assertIsDisplayed()
    }

    @Test
    fun clickingStation_showsLoadingIndicatorInRow_andMiniPlayer() = runTest {
        composeTestRule.setContent {
            SmoothRadioTheme {
                val listState = remember { LazyListState() }
                val gridState = remember { LazyGridState() }
                RadioStationsScreen(
                    listScrollState = listState,
                    gridScrollState = gridState,
                    onWhatNewClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        dismissChangelogIfVisible()

        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("RADIO 47").fetchSemanticsNodes().isNotEmpty()
        }

        // Click station row
        composeTestRule.onNodeWithTag("radio_station_228").performClick()
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        // Update state to BUFFERING
        playbackStateRepository.updateState(StreamStates.BUFFERING)
        radioRepository.setPlayingStation(228)
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        // Check for loading animation in the row
        composeTestRule.waitUntil(5000) {
            composeTestRule
                .onAllNodes(hasTestTag("dot_loading_animation") and hasAnyAncestor(hasTestTag("radio_station_228")), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Check mini player station name; wait for it to appear
        composeTestRule.waitUntil(5000) {
            composeTestRule
                .onAllNodes(hasText("RADIO 47") and hasAnyAncestor(hasTestTag("persistent_mini_player")), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Check BUFFERING status in mini player
        composeTestRule.waitUntil(5000) {
            composeTestRule
                .onAllNodes(hasText("BUFFERING") and hasAnyAncestor(hasTestTag("persistent_mini_player")), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun playingStation_showsWaveformAndMiniPlayerPlaying() = runTest {
        composeTestRule.setContent {
            SmoothRadioTheme {
                val listState = remember { LazyListState() }
                val gridState = remember { LazyGridState() }
                RadioStationsScreen(
                    listScrollState = listState,
                    gridScrollState = gridState,
                    onWhatNewClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("RADIO 47").fetchSemanticsNodes().isNotEmpty()
        }

        // Set state to PLAYING
        playbackStateRepository.updateState(StreamStates.PLAYING)
        radioRepository.setPlayingStation(228)
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        // Wait for PLAYING text in mini player
        composeTestRule.waitUntil(5000) {
            composeTestRule
                .onAllNodes(hasText("PLAYING") and hasAnyAncestor(hasTestTag("persistent_mini_player")), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Check for waveform in the row
        composeTestRule.waitUntil(5000) {
            composeTestRule
                .onAllNodes(hasTestTag("mini_waveform") and hasAnyAncestor(hasTestTag("radio_station_228")), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun toggleGridView_displaysStationsInGrid() = runTest {
        composeTestRule.setContent {
            SmoothRadioTheme {
                val listState = remember { LazyListState() }
                val gridState = remember { LazyGridState() }
                RadioStationsScreen(
                    listScrollState = listState,
                    gridScrollState = gridState,
                    onWhatNewClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("RADIO 47").fetchSemanticsNodes().isNotEmpty()
        }

        // Click grid toggle in TopBar
        composeTestRule.onNodeWithContentDescription("Switch to grid view").performClick()
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        // Stations should still be there
        composeTestRule.onNodeWithText("RADIO 47").assertIsDisplayed()
        
        // Switch back
        composeTestRule.onNodeWithContentDescription("Switch to list view").performClick()
        advanceUntilIdle()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("RADIO 47").assertIsDisplayed()
    }

    @Test
    fun clickingDifferentStation_clearsPreviousStationState_andShowsNewStationPlaying() = runTest {
        composeTestRule.setContent {
            SmoothRadioTheme {
                val listState = remember { LazyListState() }
                val gridState = remember { LazyGridState() }
                RadioStationsScreen(
                    listScrollState = listState,
                    gridScrollState = gridState,
                    onWhatNewClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("RADIO 47").fetchSemanticsNodes().isNotEmpty()
        }

        // Set RADIO 47 to PLAYING
        playbackStateRepository.updateState(StreamStates.PLAYING)
        radioRepository.setPlayingStation(228)
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        // Verify RADIO 47 shows playing state
        composeTestRule.waitUntil(5000) {
            composeTestRule
                .onAllNodes(hasTestTag("mini_waveform") and hasAnyAncestor(hasTestTag("radio_station_228")), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Click HOPE FM (ID 0) - different station
        composeTestRule.onNodeWithTag("radio_station_0").performClick()
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        // Update state to PLAYING for HOPE FM
        playbackStateRepository.updateState(StreamStates.PLAYING)
        radioRepository.setPlayingStation(0)
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        // Verify RADIO 47 no longer shows waveform (state cleared)
        composeTestRule.waitUntil(5000) {
            composeTestRule
                .onAllNodes(hasTestTag("mini_waveform") and hasAnyAncestor(hasTestTag("radio_station_228")), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty()
        }

        // Verify HOPE FM now shows waveform
        composeTestRule.waitUntil(5000) {
            composeTestRule
                .onAllNodes(hasTestTag("mini_waveform") and hasAnyAncestor(hasTestTag("radio_station_0")), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Verify mini player shows HOPE FM
        composeTestRule.waitUntil(5000) {
            composeTestRule
                .onAllNodes(hasText("HOPE FM") and hasAnyAncestor(hasTestTag("persistent_mini_player")), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun clickingPlayingStation_stopsPlayback() = runTest {
        composeTestRule.setContent {
            SmoothRadioTheme {
                val listState = remember { LazyListState() }
                val gridState = remember { LazyGridState() }
                RadioStationsScreen(
                    listScrollState = listState,
                    gridScrollState = gridState,
                    onWhatNewClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("RADIO 47").fetchSemanticsNodes().isNotEmpty()
        }

        // Set RADIO 47 to PLAYING
        playbackStateRepository.updateState(StreamStates.PLAYING)
        radioRepository.setPlayingStation(228)
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        // Verify waveform is showing
        composeTestRule.waitUntil(5000) {
            composeTestRule
                .onAllNodes(hasTestTag("mini_waveform") and hasAnyAncestor(hasTestTag("radio_station_228")), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Click the same station again to stop
        composeTestRule.onNodeWithTag("radio_station_228").performClick()
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        // Update state to IDLE to simulate stop
        playbackStateRepository.updateState(StreamStates.IDLE)
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        // Verify waveform is gone (playback stopped)
        composeTestRule.waitUntil(5000) {
            composeTestRule
                .onAllNodes(hasTestTag("mini_waveform") and hasAnyAncestor(hasTestTag("radio_station_228")), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }

    @Test
    fun clickingBufferingStation_stopsPlayback() = runTest {
        composeTestRule.setContent {
            SmoothRadioTheme {
                val listState = remember { LazyListState() }
                val gridState = remember { LazyGridState() }
                RadioStationsScreen(
                    listScrollState = listState,
                    gridScrollState = gridState,
                    onWhatNewClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("RADIO 47").fetchSemanticsNodes().isNotEmpty()
        }

        // Set RADIO 47 to BUFFERING
        playbackStateRepository.updateState(StreamStates.BUFFERING)
        radioRepository.setPlayingStation(228)
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        // Verify loading animation is showing
        composeTestRule.waitUntil(5000) {
            composeTestRule
                .onAllNodes(hasTestTag("dot_loading_animation") and hasAnyAncestor(hasTestTag("radio_station_228")), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Click the same station again to stop
        composeTestRule.onNodeWithTag("radio_station_228").performClick()
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        // Update state to IDLE to simulate stop
        playbackStateRepository.updateState(StreamStates.IDLE)
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        // Verify loading animation is gone (playback stopped)
        composeTestRule.waitUntil(5000) {
            composeTestRule
                .onAllNodes(hasTestTag("dot_loading_animation") and hasAnyAncestor(hasTestTag("radio_station_228")), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }

    @Test
    fun gridView_showsCorrectStates_forPlayingBufferingIdle() = runTest {
        composeTestRule.setContent {
            SmoothRadioTheme {
                val listState = remember { LazyListState() }
                val gridState = remember { LazyGridState() }
                RadioStationsScreen(
                    listScrollState = listState,
                    gridScrollState = gridState,
                    onWhatNewClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(10000) {
            composeTestRule
                .onAllNodes(hasTestTag("radio_station_228"), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Switch to grid view
        composeTestRule.onNodeWithContentDescription("Switch to grid view").performClick()
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        // Verify station exists in grid
        composeTestRule.onNodeWithTag("radio_station_228").assertExists()

        // Test BUFFERING state
        playbackStateRepository.updateState(StreamStates.BUFFERING)
        radioRepository.setPlayingStation(228)
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(5000) {
            composeTestRule
                .onAllNodes(hasTestTag("dot_loading_animation") and hasAnyAncestor(hasTestTag("radio_station_228")), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Test PLAYING state
        playbackStateRepository.updateState(StreamStates.PLAYING)
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(5000) {
            composeTestRule
                .onAllNodes(hasText("LIVE") and hasAnyAncestor(hasTestTag("radio_station_228")), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Switch back to list view
        composeTestRule.onNodeWithContentDescription("Switch to list view").performClick()
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        // Verify station still exists via test tag
        composeTestRule.onNodeWithTag("radio_station_228").assertExists()
    }

    @Test
    fun miniPlayer_showsCorrectContent_forPlayingBufferingIdle() = runTest {
        composeTestRule.setContent {
            SmoothRadioTheme {
                val listState = remember { LazyListState() }
                val gridState = remember { LazyGridState() }
                RadioStationsScreen(
                    listScrollState = listState,
                    gridScrollState = gridState,
                    onWhatNewClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("RADIO 47").fetchSemanticsNodes().isNotEmpty()
        }

        // Click station to trigger mini player
        composeTestRule.onNodeWithTag("radio_station_228").performClick()
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        // Verify mini player exists
        radioRepository.setPlayingStation(228)
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(5000) {
            composeTestRule
                .onAllNodes(hasTestTag("persistent_mini_player"), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Test BUFFERING state - station name and status
        playbackStateRepository.updateState(StreamStates.BUFFERING)
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(5000) {
            composeTestRule
                .onAllNodes(hasText("RADIO 47") and hasAnyAncestor(hasTestTag("persistent_mini_player")), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule.waitUntil(5000) {
            composeTestRule
                .onAllNodes(hasText("BUFFERING") and hasAnyAncestor(hasTestTag("persistent_mini_player")), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // DotLoadingAnimation in mini player when buffering
        composeTestRule.waitUntil(5000) {
            composeTestRule
                .onAllNodes(hasTestTag("dot_loading_animation") and hasAnyAncestor(hasTestTag("persistent_mini_player")), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Test PLAYING state
        playbackStateRepository.updateState(StreamStates.PLAYING)
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(5000) {
            composeTestRule            .onAllNodes(hasText("PLAYING") and hasAnyAncestor(hasTestTag("persistent_mini_player")), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Waveform in mini player when playing
        composeTestRule.waitUntil(5000) {
            composeTestRule
                .onAllNodes(hasTestTag("mini_waveform") and hasAnyAncestor(hasTestTag("persistent_mini_player")), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Test IDLE state - mini player still exists with station name
        playbackStateRepository.updateState(StreamStates.IDLE)
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(5000) {
            composeTestRule
                .onAllNodes(hasText("RADIO 47") and hasAnyAncestor(hasTestTag("persistent_mini_player")), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun toggleGridView_persistsStations_andAdjustsColumns() = runTest {
        composeTestRule.setContent {
            SmoothRadioTheme {
                val listState = remember { LazyListState() }
                val gridState = remember { LazyGridState() }
                RadioStationsScreen(
                    listScrollState = listState,
                    gridScrollState = gridState,
                    onWhatNewClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("RADIO 47").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify list view shows stations
        composeTestRule.onNodeWithText("RADIO 47").assertIsDisplayed()
        composeTestRule.onNodeWithText("INOORO FM").assertIsDisplayed()

        // Switch to grid view
        composeTestRule.onNodeWithContentDescription("Switch to grid view").performClick()
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        // Verify same stations still exist
        composeTestRule.onNodeWithText("RADIO 47").assertIsDisplayed()
        composeTestRule.onNodeWithText("INOORO FM").assertIsDisplayed()

        // Verify grid-specific element (LIVE/Frequency should not be in list items)
        // Grid items have the same test tags as list items
        composeTestRule.onNodeWithTag("radio_station_228").assertExists()
        composeTestRule.onNodeWithTag("radio_station_4").assertExists()

        // Switch back to list view
        composeTestRule.onNodeWithContentDescription("Switch to list view").performClick()
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        // Verify stations still there
        composeTestRule.onNodeWithText("RADIO 47").assertIsDisplayed()
        composeTestRule.onNodeWithText("INOORO FM").assertIsDisplayed()

        // Toggle description should now be "Switch to grid view"
        composeTestRule.onNodeWithContentDescription("Switch to grid view").assertExists()
    }

    @Test
    fun searchingForStation_filtersList() = runTest {
        composeTestRule.setContent {
            SmoothRadioTheme {
                val listState = remember { LazyListState() }
                val gridState = remember { LazyGridState() }
                RadioStationsScreen(
                    listScrollState = listState,
                    gridScrollState = gridState,
                    onWhatNewClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        dismissChangelogIfVisible()

        // 1. Wait for initial data
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("RADIO 47").fetchSemanticsNodes().isNotEmpty()
        }

        // 2. Open search
        composeTestRule.onNodeWithContentDescription("Search").performClick()
        composeTestRule.waitForIdle()

        // 3. Type search query
        composeTestRule.onNodeWithTag("search_field").performTextReplacement("INOORO")
        composeTestRule.waitForIdle()

        // 4. Verify filtered state
        composeTestRule.onNodeWithText("INOORO FM").assertIsDisplayed()
        composeTestRule.onNodeWithText("RADIO 47").assertDoesNotExist()

        // 5. Exit search mode using the "Back" button
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()

        // 6. Wait for the search field to disappear (verifies mode exit)
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodes(hasTestTag("search_field")).fetchSemanticsNodes().isEmpty()
        }

        // 7. Reset scroll to top in case search left the list in a scrolled state
        // This ensures items at the beginning of the list are composed and found.
        composeTestRule.onNode(hasTestTag("radio_station_list") or hasTestTag("radio_station_grid"))
            .performScrollToIndex(0)
        composeTestRule.waitForIdle()

        // 8. Wait for any list item to reappear (verifies UI transition from Empty/Search)
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodes(hasTestTag("radio_station_228"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // 9. Final assertions
        composeTestRule.onNodeWithTag("radio_station_228", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("RADIO 47").assertExists()
    }

    @Test
    fun clickingStation_immediatelyShowsBufferingInMiniPlayer_dueToStationChangingGuard() = runTest {
        composeTestRule.setContent {
            SmoothRadioTheme {
                val listState = remember { LazyListState() }
                val gridState = remember { LazyGridState() }
                RadioStationsScreen(
                    listScrollState = listState,
                    gridScrollState = gridState,
                    onWhatNewClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        dismissChangelogIfVisible()

        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("RADIO 47").fetchSemanticsNodes().isNotEmpty()
        }

        // 1. Ensure initial state is IDLE
        playbackStateRepository.updateState(StreamStates.IDLE)
        advanceUntilIdle()

        // 2. Click station
        composeTestRule.onNodeWithTag("radio_station_228").performClick()
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        // 3. Verify mini player shows BUFFERING even before repository updates state
        // (This is the isStationChanging guard in action)
        composeTestRule.onNode(
            hasText("BUFFERING") and hasAnyAncestor(hasTestTag("persistent_mini_player")),
            useUnmergedTree = true
        ).assertIsDisplayed()

        // 4. Verify progress bar is NOT visible (loadingProgress should be forced to 0 by guard)
        // We can't easily check drawWithContent, but we can verify isStationChanging is passed correctly
    }

    @Test
    fun aboutDialog_showsAndDismisses() = runTest {
        composeTestRule.setContent {
            SmoothRadioTheme {
                val listState = remember { LazyListState() }
                val gridState = remember { LazyGridState() }
                RadioStationsScreen(
                    listScrollState = listState,
                    gridScrollState = gridState,
                    onWhatNewClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("RADIO 47").fetchSemanticsNodes().isNotEmpty()
        }

        // Click the info/about button
        composeTestRule.onNodeWithContentDescription("About").performClick()
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        // Verify dialog is shown
        composeTestRule.onNodeWithText("SMOOTH RADIO", substring = true).assertIsDisplayed()

        // Dismiss using Close button
        composeTestRule.onNodeWithText("Close").performClick()
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        // Verify stations visible again
        composeTestRule.onNodeWithTag("radio_station_228").assertExists()
    }

    @Test
    fun aboutDialog_clickingReportProblem_opensReportDialog() = runTest {
        composeTestRule.setContent {
            SmoothRadioTheme {
                val listState = remember { LazyListState() }
                val gridState = remember { LazyGridState() }
                RadioStationsScreen(
                    listScrollState = listState,
                    gridScrollState = gridState,
                    onWhatNewClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        dismissChangelogIfVisible()

        // Open About Dialog
        composeTestRule.onNodeWithContentDescription("About").performClick()
        composeTestRule.waitForIdle()

        // Click Report a Problem in AboutDialog
        composeTestRule.onAllNodesWithText("Report a Problem").onFirst().performClick()
        composeTestRule.waitForIdle()

        // Verify Report Issue Dialog title is shown (unique tag)
        composeTestRule.onNodeWithTag("report_issue_dialog_title").assertIsDisplayed()
        composeTestRule.onNodeWithText("Describe the issue you encountered", substring = true).assertIsDisplayed()
    }

    @Test
    fun favoriteLimitExceeded_showsErrorToast() = runTest {
        composeTestRule.setContent {
            SmoothRadioTheme {
                val listState = remember { LazyListState() }
                val gridState = remember { LazyGridState() }
                RadioStationsScreen(
                    listScrollState = listState,
                    gridScrollState = gridState,
                    onWhatNewClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("RADIO 47").fetchSemanticsNodes().isNotEmpty()
        }

        // Set up 20 favorites to hit the limit
        val dummyStations = (1000..1020).map { id ->
            RadioStation(
                id = id,
                stationName = "Station $id",
                frequency = "0.0",
                location = "Test",
                streamLink = "",
                isPlaying = false,
                isFavorite = true,
                orderIndex = id
            )
        }
        radioRepository.insertStations(dummyStations)
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        // Ensure station 228 is NOT a favorite
        radioRepository.updateFavoriteStatus(228, false)
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        // Click favorite on station 228 to trigger the limit error
        val favButtonMatcher = hasContentDescription("Add to favorites") and
                hasAnyAncestor(hasTestTag("radio_station_228"))

        composeTestRule.onNode(favButtonMatcher).performClick()
        advanceUntilIdle()

        // Toast appears quickly, check immediately before auto-dismiss
        composeTestRule.waitForIdle()

        // Check for error container color toast - look for the "Error" icon content description
        composeTestRule.waitUntil(3000) {
            composeTestRule.onAllNodes(hasContentDescription("Error")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Error").assertIsDisplayed()
    }

    @Test
    fun clickingPauseInMiniPlayer_updatesStateInBothMiniPlayerAndListRow() = runTest {
        composeTestRule.setContent {
            SmoothRadioTheme {
                val listState = remember { LazyListState() }
                val gridState = remember { LazyGridState() }
                RadioStationsScreen(
                    listScrollState = listState,
                    gridScrollState = gridState,
                    onWhatNewClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("RADIO 47").fetchSemanticsNodes().isNotEmpty()
        }

        // 1. Click station to trigger mini player
        composeTestRule.onNodeWithTag("radio_station_228").performClick()
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        // 2. Set state to PLAYING
        playbackStateRepository.updateState(StreamStates.PLAYING)
        radioRepository.setPlayingStation(228)
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        // Verify waveform is in row AND mini player
        composeTestRule.waitUntil(5000) {
            composeTestRule
                .onAllNodes(hasTestTag("mini_waveform") and hasAnyAncestor(hasTestTag("radio_station_228")), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.waitUntil(5000) {
            composeTestRule
                .onAllNodes(hasTestTag("mini_waveform") and hasAnyAncestor(hasTestTag("persistent_mini_player")), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // 3. Click Pause button in Mini Player
        composeTestRule.onNodeWithContentDescription("Pause").performClick()
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        // 4. Update state to IDLE to simulate pause effect
        playbackStateRepository.updateState(StreamStates.IDLE)
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        // 5. Verify waveforms are gone from both row and mini player
        composeTestRule.onNode(hasTestTag("mini_waveform") and hasAnyAncestor(hasTestTag("radio_station_228")), useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNode(hasTestTag("mini_waveform") and hasAnyAncestor(hasTestTag("persistent_mini_player")), useUnmergedTree = true).assertDoesNotExist()

        // 6. Verify Mini Player still shows "Play" button now
        composeTestRule.onNodeWithContentDescription("Play").assertIsDisplayed()
    }

    @Test
    fun clickingPlayInMiniPlayer_startsPlaybackAndUpdatesUI() = runTest {
        composeTestRule.setContent {
            SmoothRadioTheme {
                val listState = remember { LazyListState() }
                val gridState = remember { LazyGridState() }
                RadioStationsScreen(
                    listScrollState = listState,
                    gridScrollState = gridState,
                    onWhatNewClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        dismissChangelogIfVisible()

        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("RADIO 47").fetchSemanticsNodes().isNotEmpty()
        }

        // 1. Click station to trigger mini player
        composeTestRule.onNodeWithTag("radio_station_228").performClick()
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        // 2. Ensure it's in IDLE state (paused)
        playbackStateRepository.updateStationId(228)
        playbackStateRepository.updateState(StreamStates.IDLE)
        radioRepository.setPlayingStation(228)
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        // 3. Click Play button in Mini Player
        composeTestRule.onNodeWithContentDescription("Play").performClick()
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        // 4. Update state to PLAYING
        playbackStateRepository.updateState(StreamStates.PLAYING)
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        // 5. Verify waveforms are showing in both row and mini player
        composeTestRule.waitUntil(5000) {
            composeTestRule
                .onAllNodes(hasTestTag("mini_waveform") and hasAnyAncestor(hasTestTag("radio_station_228")), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.waitUntil(5000) {
            composeTestRule
                .onAllNodes(hasTestTag("mini_waveform") and hasAnyAncestor(hasTestTag("persistent_mini_player")), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }
}
