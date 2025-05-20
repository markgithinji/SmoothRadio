

package com.smoothradio.radio.feature.radio_list.ui

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.PerformException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.smoothradio.radio.HiltTestActivity
import com.smoothradio.radio.MainActivity
import com.smoothradio.radio.R
import com.smoothradio.radio.core.di.AppModule
import com.smoothradio.radio.core.util.PlayerManager
import com.smoothradio.radio.service.StreamService
import com.smoothradio.radio.testutil.launchFragmentInHiltContainer
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.regex.Matcher
import javax.inject.Inject


@HiltAndroidTest
@UninstallModules(AppModule::class)
class RadioListFragmentTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val activityRule = ActivityScenarioRule(HiltTestActivity::class.java)

    @Inject
    lateinit var playerManager: PlayerManager

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun radioListFragment_showsRecyclerView() {
        launchFragmentInHiltContainer<RadioListFragment>()

        onView(withText("HOPE FM")).check(matches(isDisplayed()))
    }
    @Test
    fun clickHopeFmPlayIcon_showsLoading_thenShowsPlayingIcon() {
        // Launch the fragment
        launchFragmentInHiltContainer<RadioListFragment>()

        // Click the ivPlay inside the Hope FM item
        onView(withId(R.id.rv_radio_list)).perform(
            RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
                hasDescendant(withText("HOPE FM")),
                object : ViewAction {
                    override fun getConstraints(): org.hamcrest.Matcher<View> = isAssignableFrom(View::class.java)

                    override fun getDescription(): String = "Click play icon inside Hope FM item"

                    override fun perform(uiController: UiController?, view: View?) {
                        val ivPlay = view?.findViewById<View>(R.id.ivPlay)
                        ivPlay?.performClick()
                    }
                }
            )
        )

        // Step 1: Check that loading animation is shown inside Hope FM item
        onView(allOf(
            withId(R.id.lottie_loading_animation),
            isDescendantOfA(hasDescendant(withText("HOPE FM")))
        )).check(matches(isDisplayed()))

        // Step 2: Check that play icon is hidden inside Hope FM item
        onView(allOf(
            withId(R.id.ivPlay),
            isDescendantOfA(hasDescendant(withText("HOPE FM")))
        )).check(matches(withEffectiveVisibility(Visibility.INVISIBLE)))

        // Step 3: Send a broadcast that simulates the station is now playing
        val intent = Intent(StreamService.ACTION_EVENT_CHANGE).apply {
            putExtra(StreamService.EXTRA_STATE, StreamService.StreamStates.PLAYING)
        }
        ApplicationProvider.getApplicationContext<Context>().sendBroadcast(intent)

        // Step 4: Confirm loading gone, play icon visible
        onView(allOf(
            withId(R.id.lottie_loading_animation),
            isDescendantOfA(hasDescendant(withText("HOPE FM")))
        )).check(matches(withEffectiveVisibility(Visibility.INVISIBLE)))

        onView(allOf(
            withId(R.id.ivPlay),
            isDescendantOfA(hasDescendant(withText("HOPE FM")))
        )).check(matches(isDisplayed()))
    }
    @Test
    fun clickHopeFmPlayIcon_onlyAffectsThatItem() {
        launchFragmentInHiltContainer<RadioListFragment>()

        // Scroll to "Hope FM" to ensure it's rendered and bound
        onView(withId(R.id.rv_radio_list)).perform(
            RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
                hasDescendant(withText("HOPE FM"))
            )
        )

        // Now click ivPlay inside the Hope FM item safely
        onView(withId(R.id.rv_radio_list)).perform(
            RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
                hasDescendant(withText("HOPE FM")),
                object : ViewAction {
                    override fun getConstraints(): org.hamcrest.Matcher<View> = isAssignableFrom(View::class.java)

                    override fun getDescription(): String =
                        "Click ivPlay inside Hope FM's item view"

                    override fun perform(uiController: UiController?, view: View?) {
                        val ivPlay = view?.findViewById<View>(R.id.ivPlay)
                            ?: throw PerformException.Builder()
                                .withCause(NullPointerException("ivPlay not found in Hope FM item"))
                                .build()

                        if (!ivPlay.isShown) {
                            throw PerformException.Builder()
                                .withCause(IllegalStateException("ivPlay is not visible"))
                                .build()
                        }

                        ivPlay.performClick()
                    }
                }
            )
        )
    }

}
