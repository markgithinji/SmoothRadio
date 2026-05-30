package com.smoothradio.radio.feature.player.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smoothradio.radio.HiltTestActivity
import com.smoothradio.radio.R
import com.smoothradio.radio.core.domain.model.RadioStation
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
class PlayerScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    @Inject
    lateinit var radioRepository: RadioRepository

    @Inject
    lateinit var playbackStateRepository: PlaybackStateRepository

    private val testStation = RadioStation(
        id = 1,
        stationName = "HOPE FM",
        frequency = "93.3",
        location = "Nairobi",
        streamLink = "url",
        isPlaying = true,
        isFavorite = false,
        orderIndex = 0
    )

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun playerScreen_showsEmptyState_whenNoStationIsPlaying() {
        runBlocking {
            radioRepository.clearAllStations()
        }

        composeTestRule.setContent {
            SmoothRadioTheme {
                PlayerScreen()
            }
        }

        composeTestRule.onNodeWithText("No station playing").assertIsDisplayed()
    }

    @Test
    fun playerScreen_displaysStationInfo_whenStationIsPlaying() {
        runBlocking {
            radioRepository.insertStations(listOf(testStation))
            radioRepository.setPlayingStation(testStation.id)
        }

        composeTestRule.setContent {
            SmoothRadioTheme {
                PlayerScreen()
            }
        }

        // Wait for content
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("HOPE FM").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("HOPE FM").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("HOPE FM logo").assertIsDisplayed()
    }

    @Test
    fun playerScreen_showsBufferingState() {
        runBlocking {
            radioRepository.insertStations(listOf(testStation))
            radioRepository.setPlayingStation(testStation.id)
        }
        playbackStateRepository.updateState(StreamStates.BUFFERING)

        composeTestRule.setContent {
            SmoothRadioTheme {
                PlayerScreen()
            }
        }

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("BUFFERING").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("BUFFERING").assertIsDisplayed()
        // Check for the dot loading animation (using unmerged tree for nested components)
        composeTestRule.onNodeWithTag("dot_loading_animation", useUnmergedTree = true).assertExists()
    }

    @Test
    fun playerScreen_showsPlayingState() {
        runBlocking {
            radioRepository.insertStations(listOf(testStation))
            radioRepository.setPlayingStation(testStation.id)
        }
        playbackStateRepository.updateState(StreamStates.PLAYING)

        composeTestRule.setContent {
            SmoothRadioTheme {
                PlayerScreen()
            }
        }

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("NOW PLAYING").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("NOW PLAYING").assertIsDisplayed()
    }

    @Test
    fun playerScreen_updatesMetadata() {
        runBlocking {
            radioRepository.insertStations(listOf(testStation))
            radioRepository.setPlayingStation(testStation.id)
        }
        playbackStateRepository.updateState(StreamStates.PLAYING)
        playbackStateRepository.updateMetadata("Artist - Song Title")

        composeTestRule.setContent {
            SmoothRadioTheme {
                PlayerScreen()
            }
        }

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Artist - Song Title").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Artist - Song Title").assertIsDisplayed()
    }

    @Test
    fun playerScreen_clicksPlayPause() {
        runBlocking {
            radioRepository.insertStations(listOf(testStation))
            radioRepository.setPlayingStation(testStation.id)
        }
        playbackStateRepository.updateState(StreamStates.PLAYING)

        composeTestRule.setContent {
            SmoothRadioTheme {
                PlayerScreen()
            }
        }

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithContentDescription("Pause").fetchSemanticsNodes().isNotEmpty()
        }

        // Initially shows pause button because it's playing
        composeTestRule.onNodeWithContentDescription("Pause").performClick()
        
        // Note: The UI won't immediately change to "Play" icon unless our FakeRepo updates.
        // But we verified the click can be performed.
    }

    @Test
    fun playerScreen_clicksNextPrevious() {
        val secondStation = testStation.copy(id = 2, stationName = "NEXT STATION", orderIndex = 1)
        runBlocking {
            radioRepository.insertStations(listOf(testStation, secondStation))
            radioRepository.setPlayingStation(testStation.id)
        }

        composeTestRule.setContent {
            SmoothRadioTheme {
                PlayerScreen()
            }
        }

        composeTestRule.onNodeWithContentDescription("Next").performClick()
        
        // Should update to NEXT STATION
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("NEXT STATION").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("NEXT STATION").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Previous").performClick()

        // Should update back to HOPE FM
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("HOPE FM").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("HOPE FM").assertIsDisplayed()
    }

    @Test
    fun playerScreen_opensEqualizerDialog() {
        runBlocking {
            radioRepository.insertStations(listOf(testStation))
            radioRepository.setPlayingStation(testStation.id)
        }

        composeTestRule.setContent {
            SmoothRadioTheme {
                PlayerScreen()
            }
        }

        composeTestRule.onNodeWithContentDescription("Equalizer").performClick()
        composeTestRule.onNodeWithText("Equalizer").assertIsDisplayed()
        composeTestRule.onNodeWithText("60 Hz").assertIsDisplayed()
    }

    @Test
    fun playerScreen_opensSleepTimerDialog() {
        runBlocking {
            radioRepository.insertStations(listOf(testStation))
            radioRepository.setPlayingStation(testStation.id)
        }

        composeTestRule.setContent {
            SmoothRadioTheme {
                PlayerScreen()
            }
        }

        // The Sleep button is in the ActionButtonsRow which only shows if layoutConfig.showSecondRow is true.
        // showSecondRow = screenHeight > 740.dp
        // On many standard emulators (like Pixel 4), height is enough.

        // We use onNodeWithContentDescription to target the IconButton's Icon, which handles the click.
        // onNodeWithText("Sleep") would target the Text composable which is not clickable.
        composeTestRule.onNodeWithContentDescription("Sleep").performClick()
        
        composeTestRule.waitUntil(3000) {
            composeTestRule.onAllNodesWithText("Sleep Timer").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Sleep Timer").assertIsDisplayed()
        composeTestRule.onNodeWithText("5 minutes").assertIsDisplayed()
    }
}
