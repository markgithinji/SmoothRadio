package com.smoothradio.radio.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * This test class generates a basic startup baseline profile for the target package.
 *
 * We recommend you start with this but add important user flows to the profile to improve their performance.
 * Refer to the [baseline profile documentation](https://d.android.com/topic/performance/baselineprofiles)
 * for more information.
 *
 * You can run the generator with the "Generate Baseline Profile" run configuration in Android Studio or
 * the equivalent `generateBaselineProfile` gradle task:
 * ```
 * ./gradlew :app:generateReleaseBaselineProfile
 * ```
 * The run configuration runs the Gradle task and applies filtering to run only the generators.
 *
 * Check [documentation](https://d.android.com/topic/performance/benchmarking/macrobenchmark-instrumentation-args)
 * for more information about available instrumentation arguments.
 *
 * After you run the generator, you can verify the improvements running the [StartupBenchmarks] benchmark.
 *
 * When using this class to generate a baseline profile, only API 33+ or rooted API 28+ are supported.
 *
 * The minimum required version of androidx.benchmark to generate a baseline profile is 1.2.0.
 **/
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        // The application id for the running build variant is read from the instrumentation arguments.
        val targetAppId = InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: throw Exception("targetAppId not passed as instrumentation runner arg")

        rule.collect(
            packageName = targetAppId,
            includeInStartupProfile = true
        ) {
            pressHome()
            startActivityAndWait()

            // 1. Stations Tab (Initial) - Scroll through the list
            val stationList = device.wait(Until.findObject(By.scrollable(true)), 5000)
            stationList?.scroll(Direction.DOWN, 0.5f)
            device.waitForIdle()

            // 2. Switch to Live Tab (Player)
            val liveTab = device.findObject(By.text("Live"))
            liveTab?.click()
            device.waitForIdle()

            // 3. Switch to Discover Tab
            val discoverTab = device.findObject(By.text("Discover"))
            discoverTab?.click()
            device.waitForIdle()
            
            // Scroll a bit in Discover
            val discoverList = device.wait(Until.findObject(By.scrollable(true)), 2000)
            discoverList?.scroll(Direction.DOWN, 0.5f)
            device.waitForIdle()

            // 4. Back to Stations and start a station
            val stationsTab = device.findObject(By.text("Stations"))
            stationsTab?.click()
            device.waitForIdle()

            // Scroll back up to ensure HOPE FM is in view
            val stationListBack = device.wait(Until.findObject(By.scrollable(true)), 5000)
            stationListBack?.scroll(Direction.UP, 1.0f)
            device.waitForIdle()

            // Wait for stations to load and click HOPE FM
            device.wait(Until.hasObject(By.text("HOPE FM")), 10000)
            val hopeFm = device.findObject(By.text("HOPE FM"))
            if (hopeFm != null) {
                hopeFm.click()
                // Exercise playback code for a few seconds
                Thread.sleep(3000)
                device.waitForIdle()
            }
        }
    }
}
