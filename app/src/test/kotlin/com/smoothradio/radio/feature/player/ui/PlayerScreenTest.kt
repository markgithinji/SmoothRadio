package com.smoothradio.radio.feature.player.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        orderIndex = 0,
    )

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun playerScreen_showsEmptyState_whenNoStationIsPlaying() = runTest {
        radioRepository.clearAllStations()
        advanceUntilIdle()

        composeTestRule.setContent {
            SmoothRadioTheme {
                PlayerScreen()
            }
        }

        composeTestRule.onNodeWithText("No station playing").assertIsDisplayed()
    }

    @Test
    fun playerScreen_displaysStationInfo_whenStationIsPlaying() = runTest {
        radioRepository.insertStations(listOf(testStation))
        radioRepository.setPlayingStation(testStation.id)
        advanceUntilIdle()

        composeTestRule.setContent {
            SmoothRadioTheme {
                PlayerScreen()
            }
        }

        // Use waitForIdle to ensure hierarchy is attached
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("HOPE FM").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("HOPE FM logo").assertIsDisplayed()
    }

    @Test
    fun playerScreen_showsBufferingState() = runTest {
        radioRepository.insertStations(listOf(testStation))
        radioRepository.setPlayingStation(testStation.id)
        playbackStateRepository.updateState(StreamStates.BUFFERING)
        advanceUntilIdle()

        composeTestRule.setContent {
            SmoothRadioTheme {
                PlayerScreen()
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("BUFFERING").assertIsDisplayed()
        // Check for the dot loading animation (using unmerged tree for nested components)
        composeTestRule.onNodeWithTag("dot_loading_animation", useUnmergedTree = true).assertExists()
    }

    @Test
    fun playerScreen_showsPlayingState() = runTest {
        radioRepository.insertStations(listOf(testStation))
        radioRepository.setPlayingStation(testStation.id)
        playbackStateRepository.updateState(StreamStates.PLAYING)
        advanceUntilIdle()

        composeTestRule.setContent {
            SmoothRadioTheme {
                PlayerScreen()
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("NOW PLAYING").assertIsDisplayed()
    }

    @Test
    fun playerScreen_updatesMetadata() = runTest {
        radioRepository.insertStations(listOf(testStation))
        radioRepository.setPlayingStation(testStation.id)
        playbackStateRepository.updateState(StreamStates.PLAYING)
        playbackStateRepository.updateMetadata("Artist - Song Title")
        advanceUntilIdle()

        composeTestRule.setContent {
            SmoothRadioTheme {
                PlayerScreen()
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Artist - Song Title").assertIsDisplayed()
    }

    @Test
    fun playerScreen_clicksPlayPause() = runTest {
        radioRepository.insertStations(listOf(testStation))
        radioRepository.setPlayingStation(testStation.id)
        playbackStateRepository.updateState(StreamStates.PLAYING)
        advanceUntilIdle()

        composeTestRule.setContent {
            SmoothRadioTheme {
                PlayerScreen()
            }
        }

        composeTestRule.waitForIdle()

        // Initially shows pause button because it's playing
        composeTestRule.onNodeWithContentDescription("Pause").performClick()
        advanceUntilIdle()
    }

    @Test
    fun playerScreen_clicksNextPrevious_immediatelyShowsBuffering() = runTest {
        val secondStation = testStation.copy(id = 2, stationName = "NEXT STATION", orderIndex = 1)
        radioRepository.insertStations(listOf(testStation, secondStation))
        radioRepository.setPlayingStation(testStation.id)
        playbackStateRepository.updateState(StreamStates.PLAYING)
        advanceUntilIdle()

        composeTestRule.setContent {
            SmoothRadioTheme {
                PlayerScreen()
            }
        }

        composeTestRule.waitForIdle()

        // Before click - showing PLAYING
        composeTestRule.onNodeWithText("NOW PLAYING").assertIsDisplayed()

        // Click Next
        composeTestRule.onNodeWithContentDescription("Next").performClick()
        
        // NO advanceUntilIdle yet to check immediate state
        // It should show BUFFERING immediately because of the reset in VM
        composeTestRule.onNodeWithText("BUFFERING").assertIsDisplayed()
        composeTestRule.onNodeWithText("NEXT STATION").assertIsDisplayed()

        // Now advance and simulate player ready
        advanceUntilIdle()
        playbackStateRepository.updateState(StreamStates.PLAYING)
        radioRepository.setPlayingStation(2)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("NOW PLAYING").assertIsDisplayed()
        composeTestRule.onNodeWithText("NEXT STATION").assertIsDisplayed()
    }

    @Test
    fun playerScreen_showsSeekBar_whenSufficientHeight() = runTest {
        radioRepository.insertStations(listOf(testStation))
        radioRepository.setPlayingStation(testStation.id)
        advanceUntilIdle()

        composeTestRule.setContent {
            SmoothRadioTheme {
                PlayerScreen()
            }
        }

        composeTestRule.waitForIdle()

        // The SeekBar contains text for current time (00:00)
        composeTestRule.onNodeWithText("00:00").assertIsDisplayed()
        composeTestRule.onNodeWithText("LIVE").assertIsDisplayed()
    }

    @Test
    fun playerScreen_clicksSeekForwardAndBack() = runTest {
        radioRepository.insertStations(listOf(testStation))
        radioRepository.setPlayingStation(testStation.id)
        playbackStateRepository.updatePosition(50000L) // 50s
        advanceUntilIdle()

        composeTestRule.setContent {
            SmoothRadioTheme {
                PlayerScreen()
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Seek Forward").performClick()
        advanceUntilIdle()
        composeTestRule.onNodeWithContentDescription("Seek Back").performClick()
        advanceUntilIdle()
        
        composeTestRule.onNodeWithContentDescription("Seek Forward").assertExists()
        composeTestRule.onNodeWithContentDescription("Seek Back").assertExists()
    }

    @Test
    fun playerScreen_opensEqualizerDialog() = runTest {
        radioRepository.insertStations(listOf(testStation))
        radioRepository.setPlayingStation(testStation.id)
        advanceUntilIdle()

        composeTestRule.setContent {
            SmoothRadioTheme {
                PlayerScreen()
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Equalizer").performClick()
        advanceUntilIdle()
        composeTestRule.onNodeWithText("Equalizer").assertIsDisplayed()
        composeTestRule.onNodeWithText("60 Hz").assertIsDisplayed()
    }

    @Test
    fun playerScreen_opensSleepTimerDialog() = runTest {
        radioRepository.insertStations(listOf(testStation))
        radioRepository.setPlayingStation(testStation.id)
        advanceUntilIdle()

        composeTestRule.setContent {
            SmoothRadioTheme {
                PlayerScreen()
            }
        }

        composeTestRule.waitForIdle()

        // The Sleep button is in the ActionButtonsRow
        composeTestRule.onNodeWithContentDescription("Sleep").performClick()
        advanceUntilIdle()
        
        composeTestRule.waitUntil(3000) {
            composeTestRule.onAllNodesWithText("Sleep Timer").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Sleep Timer").assertIsDisplayed()
        composeTestRule.onNodeWithText("5 minutes").assertIsDisplayed()
    }
}
