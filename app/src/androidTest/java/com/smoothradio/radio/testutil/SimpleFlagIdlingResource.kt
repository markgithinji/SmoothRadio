package com.smoothradio.radio.testutil

import androidx.test.espresso.IdlingResource

class SimpleFlagIdlingResource(
    private val isIdleCondition: () -> Boolean
) : IdlingResource {

    @Volatile
    private var callback: IdlingResource.ResourceCallback? = null

    override fun getName(): String = "SimpleFlagIdlingResource"

    override fun isIdleNow(): Boolean {
        val idle = isIdleCondition()
        if (idle) {
            callback?.onTransitionToIdle()
        }
        return idle
    }

    override fun registerIdleTransitionCallback(cb: IdlingResource.ResourceCallback?) {
        callback = cb
    }
}