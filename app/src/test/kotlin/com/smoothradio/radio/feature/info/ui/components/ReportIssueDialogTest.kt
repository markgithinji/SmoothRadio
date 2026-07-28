package com.smoothradio.radio.feature.info.ui.components

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smoothradio.radio.core.data.repository.FakeFirebaseRepository
import com.smoothradio.radio.core.data.repository.FakeViewPreferenceRepository
import com.smoothradio.radio.feature.info.domain.usecase.GetChangelogItemsUseCase
import com.smoothradio.radio.feature.info.ui.AppInfoViewModel
import com.smoothradio.radio.ui.theme.SmoothRadioTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ReportIssueDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: AppInfoViewModel
    private lateinit var fakeFirebaseRepository: FakeFirebaseRepository
    private lateinit var fakeViewPreferenceRepository: FakeViewPreferenceRepository
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        fakeFirebaseRepository = FakeFirebaseRepository()
        fakeViewPreferenceRepository = FakeViewPreferenceRepository()
        viewModel = AppInfoViewModel(
            fakeViewPreferenceRepository,
            fakeFirebaseRepository,
            GetChangelogItemsUseCase()
        )
    }

    @Test
    fun reportIssueDialog_initiallySubmitIsDisabled() {
        composeTestRule.setContent {
            SmoothRadioTheme {
                ReportIssueDialog(
                    onDismiss = {},
                    context = context,
                    appVersion = "1.0",
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithTag("submit_report_button").assertIsNotEnabled()
    }

    @Test
    fun reportIssueDialog_enteringDescriptionEnablesSubmit() {
        composeTestRule.setContent {
            SmoothRadioTheme {
                ReportIssueDialog(
                    onDismiss = {},
                    context = context,
                    appVersion = "1.0",
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithTag("report_description").performTextInput("The stream keeps cutting off")
        composeTestRule.onNodeWithTag("submit_report_button").assertIsEnabled()
    }

    @Test
    fun reportIssueDialog_clickingSubmit_callsViewModel() {
        composeTestRule.setContent {
            SmoothRadioTheme {
                ReportIssueDialog(
                    onDismiss = {},
                    context = context,
                    appVersion = "1.0",
                    viewModel = viewModel
                )
            }
        }

        val description = "Bug report detail"
        composeTestRule.onNodeWithTag("report_description").performTextInput(description)
        composeTestRule.onNodeWithTag("submit_report_button").performClick()

        assert(fakeFirebaseRepository.lastReport?.get("description") == description)
    }

    @Test
    fun reportIssueDialog_categorySelection_updatesCategory() {
        composeTestRule.setContent {
            SmoothRadioTheme {
                ReportIssueDialog(
                    onDismiss = {},
                    context = context,
                    appVersion = "1.0",
                    viewModel = viewModel
                )
            }
        }

        // Open dropdown
        composeTestRule.onNodeWithTag("category_selector").performClick()
        
        // Select "App Crashing"
        composeTestRule.onNodeWithText("App Crashing").performClick()
        
        // Verify selected category text updated
        composeTestRule.onNodeWithText("App Crashing").assertIsDisplayed()
    }

    @Test
    fun reportIssueDialog_onSuccess_showsSuccessTextInButton() {
        composeTestRule.setContent {
            SmoothRadioTheme {
                ReportIssueDialog(
                    onDismiss = {},
                    context = context,
                    appVersion = "1.0",
                    viewModel = viewModel
                )
            }
        }

        // Enter description and click submit
        composeTestRule.onNodeWithTag("report_description").performTextInput("Test issue")
        composeTestRule.onNodeWithTag("submit_report_button").performClick()

        // Verify success state UI is shown in the button
        composeTestRule.onNodeWithText("Sent Successfully").assertIsDisplayed()
        composeTestRule.onNodeWithTag("submit_report_button").assertIsNotEnabled()
    }
}
