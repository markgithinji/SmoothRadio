package com.smoothradio.radio

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/**
 * Convenience parameter to use proper package name with regards to build type and build flavor.
 */
val PACKAGE_NAME = InstrumentationRegistry.getArguments().getString("targetAppId")
    ?: throw Exception("targetAppId not passed as instrumentation runner arg")

fun UiDevice.flingElementDownUp(element: UiObject2) {
    // Set some margin from the sides to prevent triggering system navigation
    element.setGestureMargin(displayWidth / 5)

    element.fling(Direction.DOWN)
    waitForIdle()
    element.fling(Direction.UP)
}