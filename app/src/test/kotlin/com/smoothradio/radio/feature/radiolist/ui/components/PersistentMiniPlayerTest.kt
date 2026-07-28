package com.smoothradio.radio.feature.radiolist.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.domain.model.StreamStates
import com.smoothradio.radio.ui.theme.SmoothRadioTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PersistentMiniPlayerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testStation = RadioStation(
        id = 1,
        stationName = "Test Station",
        frequency = "100.1",
        location = "Nairobi",
        streamLink = "url",
        isPlaying = false,
        isFavorite = false,
        orderIndex = 0
    )

    @Test
    fun persistentMiniPlayer_showsStationNameAndLocation_whenIdle() {
        composeTestRule.setContent {
            SmoothRadioTheme {
                PersistentMiniPlayer(
                    station = testStation,
                    playbackState = StreamStates.IDLE,
                    onPlayPauseClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Test Station").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nairobi").assertIsDisplayed()
    }

    @Test
    fun persistentMiniPlayer_showsBuffering_whenPreparing() {
        composeTestRule.setContent {
            SmoothRadioTheme {
                PersistentMiniPlayer(
                    station = testStation,
                    playbackState = StreamStates.PREPARING,
                    onPlayPauseClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("BUFFERING").assertIsDisplayed()
        // Check for DotLoadingAnimation in unmerged tree
        composeTestRule.onNodeWithTag("dot_loading_animation", useUnmergedTree = true).assertExists()
    }

    @Test
    fun persistentMiniPlayer_showsPlayingAndWaveform_whenPlaying() {
        composeTestRule.setContent {
            SmoothRadioTheme {
                PersistentMiniPlayer(
                    station = testStation,
                    playbackState = StreamStates.PLAYING,
                    onPlayPauseClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("PLAYING").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mini_waveform", useUnmergedTree = true).assertExists()
    }

    @Test
    fun persistentMiniPlayer_clicksPlayPause() {
        var clicked = false
        composeTestRule.setContent {
            SmoothRadioTheme {
                PersistentMiniPlayer(
                    station = testStation,
                    playbackState = StreamStates.IDLE,
                    onPlayPauseClick = { clicked = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("play_pause_container", useUnmergedTree = true).performClick()
        assert(clicked)
    }

    @Test
    fun persistentMiniPlayer_hidesWhenStationIsNull() {
        composeTestRule.setContent {
            SmoothRadioTheme {
                PersistentMiniPlayer(
                    station = null,
                    playbackState = StreamStates.IDLE,
                    onPlayPauseClick = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("persistent_mini_player").assertDoesNotExist()
    }
}
