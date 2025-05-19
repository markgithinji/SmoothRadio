

package com.smoothradio.radio.feature.radio_list.ui

import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.smoothradio.radio.HiltTestActivity
import com.smoothradio.radio.MainActivity
import com.smoothradio.radio.R
import com.smoothradio.radio.core.di.AppModule
import com.smoothradio.radio.testutil.launchFragmentInHiltContainer
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.regex.Matcher


@HiltAndroidTest
@UninstallModules(AppModule::class)
class RadioListFragmentTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val activityRule = ActivityScenarioRule(HiltTestActivity::class.java)

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun radioListFragment_showsRecyclerView() {
        launchFragmentInHiltContainer<RadioListFragment>()

//        waitUntilViewWithTextAppears("HOPE FM", timeoutMs = 3000)

        onView(withText("HOPE FM")).check(matches(isDisplayed()))
    }

     @Test
     fun radioListFragment_displaysStationItem() {
         launchFragmentInHiltContainer<RadioListFragment>()
//         Thread.sleep(5000)
         onView(withText("Hope FM")).check(matches(isDisplayed()))
     }
    private fun waitUntilViewWithTextAppears(text: String, timeoutMs: Long) {
        val start = System.currentTimeMillis()
        var lastError: Throwable? = null

        do {
            try {
                onView(withText(text)).check(matches(isDisplayed()))
                return
            } catch (e: Throwable) {
                lastError = e
                Thread.sleep(100)
            }
        } while (System.currentTimeMillis() - start < timeoutMs)

        throw AssertionError("View with text <$text> not found after $timeoutMs ms", lastError)
    }

}
