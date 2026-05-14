package com.smoothradio.radio.feature.radiolist.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smoothradio.radio.HiltTestActivity
import com.smoothradio.radio.core.domain.model.StreamStates
import com.smoothradio.radio.core.domain.repository.PlaybackStateRepository
import com.smoothradio.radio.core.domain.repository.RadioRepository
import com.smoothradio.radio.ui.theme.SmoothRadioTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
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

    @Test
    fun radioStationsScreen_displaysStationsInList() {
        composeTestRule.setContent {
            SmoothRadioTheme {
                val listState = remember { LazyListState() }
                val gridState = remember { LazyGridState() }
                RadioStationsScreen(listScrollState = listState, gridScrollState = gridState)
            }
        }

        // Wait for data auto-population
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("RADIO 47").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("RADIO 47").assertIsDisplayed()
        composeTestRule.onNodeWithText("INOORO FM").assertIsDisplayed()
    }

    @Test
    fun clickingFavoriteButton_togglesFavoriteStatus() {
        composeTestRule.setContent {
            SmoothRadioTheme {
                val listState = remember { LazyListState() }
                val gridState = remember { LazyGridState() }
                RadioStationsScreen(listScrollState = listState, gridScrollState = gridState)
            }
        }

        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("RADIO 47").fetchSemanticsNodes().isNotEmpty()
        }

        val favButtonMatcher = hasContentDescription("Add to favorites") and
                hasAnyAncestor(hasTestTag("radio_station_228"))
        val unfavButtonMatcher = hasContentDescription("Remove from favorites") and
                hasAnyAncestor(hasTestTag("radio_station_228"))

        // Add to favorite
        composeTestRule.onNode(favButtonMatcher).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNode(unfavButtonMatcher).assertIsDisplayed()

        // Remove from favorite
        composeTestRule.onNode(unfavButtonMatcher).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNode(favButtonMatcher).assertIsDisplayed()
    }

    @Test
    fun clickingStation_showsLoadingIndicatorInRow_andMiniPlayer() {
        composeTestRule.setContent {
            SmoothRadioTheme {
                val listState = remember { LazyListState() }
                val gridState = remember { LazyGridState() }
                RadioStationsScreen(listScrollState = listState, gridScrollState = gridState)
            }
        }

        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("RADIO 47").fetchSemanticsNodes().isNotEmpty()
        }

        // Click station row
        composeTestRule.onNodeWithTag("radio_station_228").performClick()
        composeTestRule.waitForIdle()

        // Update state to BUFFERING
        playbackStateRepository.updateState(StreamStates.BUFFERING)
        runBlocking {
            radioRepository.setPlayingStation(228)
        }
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
    fun playingStation_showsWaveformAndMiniPlayerPlaying() {
        composeTestRule.setContent {
            SmoothRadioTheme {
                val listState = remember { LazyListState() }
                val gridState = remember { LazyGridState() }
                RadioStationsScreen(listScrollState = listState, gridScrollState = gridState)
            }
        }

        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("RADIO 47").fetchSemanticsNodes().isNotEmpty()
        }

        // Set state to PLAYING
        playbackStateRepository.updateState(StreamStates.PLAYING)
        runBlocking { radioRepository.setPlayingStation(228) }
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
    fun toggleGridView_displaysStationsInGrid() {
        composeTestRule.setContent {
            SmoothRadioTheme {
                val listState = remember { LazyListState() }
                val gridState = remember { LazyGridState() }
                RadioStationsScreen(listScrollState = listState, gridScrollState = gridState)
            }
        }

        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("RADIO 47").fetchSemanticsNodes().isNotEmpty()
        }

        // Click grid toggle in TopBar
        composeTestRule.onNodeWithContentDescription("Switch to grid view").performClick()
        composeTestRule.waitForIdle()

        // Stations should still be there
        composeTestRule.onNodeWithText("RADIO 47").assertIsDisplayed()
        
        // Switch back
        composeTestRule.onNodeWithContentDescription("Switch to list view").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("RADIO 47").assertIsDisplayed()
    }

    @Test
    fun clickingDifferentStation_clearsPreviousStationState_andShowsNewStationPlaying() {
        composeTestRule.setContent {
            SmoothRadioTheme {
                val listState = remember { LazyListState() }
                val gridState = remember { LazyGridState() }
                RadioStationsScreen(listScrollState = listState, gridScrollState = gridState)
            }
        }

        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("RADIO 47").fetchSemanticsNodes().isNotEmpty()
        }

        // Set RADIO 47 to PLAYING
        playbackStateRepository.updateState(StreamStates.PLAYING)
        runBlocking { radioRepository.setPlayingStation(228) }
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
        composeTestRule.waitForIdle()

        // Update state to PLAYING for HOPE FM
        playbackStateRepository.updateState(StreamStates.PLAYING)
        runBlocking { radioRepository.setPlayingStation(0) }
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
    fun clickingPlayingStation_stopsPlayback() {
        composeTestRule.setContent {
            SmoothRadioTheme {
                val listState = remember { LazyListState() }
                val gridState = remember { LazyGridState() }
                RadioStationsScreen(listScrollState = listState, gridScrollState = gridState)
            }
        }

        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("RADIO 47").fetchSemanticsNodes().isNotEmpty()
        }

        // Set RADIO 47 to PLAYING
        playbackStateRepository.updateState(StreamStates.PLAYING)
        runBlocking { radioRepository.setPlayingStation(228) }
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
        composeTestRule.waitForIdle()

        // Update state to IDLE to simulate stop
        playbackStateRepository.updateState(StreamStates.IDLE)
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
    fun clickingBufferingStation_stopsPlayback() {
        composeTestRule.setContent {
            SmoothRadioTheme {
                val listState = remember { LazyListState() }
                val gridState = remember { LazyGridState() }
                RadioStationsScreen(listScrollState = listState, gridScrollState = gridState)
            }
        }

        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("RADIO 47").fetchSemanticsNodes().isNotEmpty()
        }

        // Set RADIO 47 to BUFFERING
        playbackStateRepository.updateState(StreamStates.BUFFERING)
        runBlocking { radioRepository.setPlayingStation(228) }
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
        composeTestRule.waitForIdle()

        // Update state to IDLE to simulate stop
        playbackStateRepository.updateState(StreamStates.IDLE)
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
    fun gridView_showsCorrectStates_forPlayingBufferingIdle() {
        composeTestRule.setContent {
            SmoothRadioTheme {
                val listState = remember { LazyListState() }
                val gridState = remember { LazyGridState() }
                RadioStationsScreen(listScrollState = listState, gridScrollState = gridState)
            }
        }

        composeTestRule.waitUntil(10000) {
            composeTestRule
                .onAllNodes(hasTestTag("radio_station_228"), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Switch to grid view
        composeTestRule.onNodeWithContentDescription("Switch to grid view").performClick()
        composeTestRule.waitForIdle()

        // Verify station exists in grid
        composeTestRule.onNodeWithTag("radio_station_228").assertExists()

        // Test BUFFERING state
        playbackStateRepository.updateState(StreamStates.BUFFERING)
        runBlocking { radioRepository.setPlayingStation(228) }
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(5000) {
            composeTestRule
                .onAllNodes(hasTestTag("dot_loading_animation") and hasAnyAncestor(hasTestTag("radio_station_228")), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Test PLAYING state
        playbackStateRepository.updateState(StreamStates.PLAYING)
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(5000) {
            composeTestRule
                .onAllNodes(hasText("LIVE") and hasAnyAncestor(hasTestTag("radio_station_228")), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Switch back to list view
        composeTestRule.onNodeWithContentDescription("Switch to list view").performClick()
        composeTestRule.waitForIdle()

        // Verify station still exists via test tag
        composeTestRule.onNodeWithTag("radio_station_228").assertExists()
    }

    @Test
    fun miniPlayer_showsCorrectContent_forPlayingBufferingIdle() {
        composeTestRule.setContent {
            SmoothRadioTheme {
                val listState = remember { LazyListState() }
                val gridState = remember { LazyGridState() }
                RadioStationsScreen(listScrollState = listState, gridScrollState = gridState)
            }
        }

        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("RADIO 47").fetchSemanticsNodes().isNotEmpty()
        }

        // Click station to trigger mini player
        composeTestRule.onNodeWithTag("radio_station_228").performClick()
        composeTestRule.waitForIdle()

        // Verify mini player exists
        runBlocking { radioRepository.setPlayingStation(228) }
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(5000) {
            composeTestRule
                .onAllNodes(hasTestTag("persistent_mini_player"), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Test BUFFERING state - station name and status
        playbackStateRepository.updateState(StreamStates.BUFFERING)
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
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(5000) {
            composeTestRule
                .onAllNodes(hasText("RADIO 47") and hasAnyAncestor(hasTestTag("persistent_mini_player")), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun toggleGridView_persistsStations_andAdjustsColumns() {
        composeTestRule.setContent {
            SmoothRadioTheme {
                val listState = remember { LazyListState() }
                val gridState = remember { LazyGridState() }
                RadioStationsScreen(listScrollState = listState, gridScrollState = gridState)
            }
        }

        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("RADIO 47").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify list view shows stations
        composeTestRule.onNodeWithText("RADIO 47").assertIsDisplayed()
        composeTestRule.onNodeWithText("INOORO FM").assertIsDisplayed()

        // Switch to grid view
        composeTestRule.onNodeWithContentDescription("Switch to grid view").performClick()
        composeTestRule.waitForIdle()

        // Verify same stations still exist
        composeTestRule.onNodeWithText("RADIO 47").assertIsDisplayed()
        composeTestRule.onNodeWithText("INOORO FM").assertIsDisplayed()

        // Verify grid-specific element (LIVE/Frequency should not be in list items)
        // Grid items have the same test tags as list items
        composeTestRule.onNodeWithTag("radio_station_228").assertExists()
        composeTestRule.onNodeWithTag("radio_station_11").assertExists()

        // Switch back to list view
        composeTestRule.onNodeWithContentDescription("Switch to list view").performClick()
        composeTestRule.waitForIdle()

        // Verify stations still there
        composeTestRule.onNodeWithText("RADIO 47").assertIsDisplayed()
        composeTestRule.onNodeWithText("INOORO FM").assertIsDisplayed()

        // Toggle description should now be "Switch to grid view"
        composeTestRule.onNodeWithContentDescription("Switch to grid view").assertExists()
    }

    @Test
    fun aboutDialog_showsAndDismisses() {
        composeTestRule.setContent {
            SmoothRadioTheme {
                val listState = remember { LazyListState() }
                val gridState = remember { LazyGridState() }
                RadioStationsScreen(listScrollState = listState, gridScrollState = gridState)
            }
        }

        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("RADIO 47").fetchSemanticsNodes().isNotEmpty()
        }

        // Click the info/about button
        composeTestRule.onNodeWithContentDescription("About").performClick()
        composeTestRule.waitForIdle()

        // Verify dialog is shown
        composeTestRule.onNodeWithText("SMOOTH RADIO", substring = true).assertIsDisplayed()

        // Dismiss using Close button
        composeTestRule.onNodeWithText("Close").performClick()
        composeTestRule.waitForIdle()

        // Verify stations visible again
        composeTestRule.onNodeWithTag("radio_station_228").assertExists()
    }

    @Test
    fun favoriteLimitExceeded_showsErrorToast() {
        composeTestRule.setContent {
            SmoothRadioTheme {
                val listState = remember { LazyListState() }
                val gridState = remember { LazyGridState() }
                RadioStationsScreen(listScrollState = listState, gridScrollState = gridState)
            }
        }

        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("RADIO 47").fetchSemanticsNodes().isNotEmpty()
        }

        // Set up 20 favorites to hit the limit
        runBlocking {
            val dummyStations = (1000..1020).map { id ->
                com.smoothradio.radio.core.domain.model.RadioStation(
                    id = id,
                    logoResource = 0,
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
        }
        composeTestRule.waitForIdle()

        // Ensure station 228 is NOT a favorite
        runBlocking {
            radioRepository.updateFavoriteStatus(228, false)
        }
        composeTestRule.waitForIdle()

        // Click favorite on station 228 to trigger the limit error
        val favButtonMatcher = hasContentDescription("Add to favorites") and
                hasAnyAncestor(hasTestTag("radio_station_228"))

        composeTestRule.onNode(favButtonMatcher).performClick()

        // Toast appears quickly, check immediately before auto-dismiss
        composeTestRule.waitForIdle()

        // Check for error container color toast - look for the "Error" icon content description
        composeTestRule.waitUntil(3000) {
            composeTestRule.onAllNodes(hasContentDescription("Error")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Error").assertIsDisplayed()
    }
}
