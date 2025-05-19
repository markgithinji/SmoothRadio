package com.smoothradio.radio.testutil

import androidx.test.espresso.IdlingResource

class PlayerIdleResource(
    private val expectedStates: Set<String>
) : IdlingResource {

    @Volatile
    private var callback: IdlingResource.ResourceCallback? = null

    private val listener = object : PlayerStateTracker.Listener {
        override fun onStateChanged(newState: String) {
            if (expectedStates.contains(newState)) {
                callback?.onTransitionToIdle()
            }
        }
    }

    override fun getName(): String = "PlayerIdleResource(${expectedStates.joinToString()})"

    override fun isIdleNow(): Boolean {
        return expectedStates.contains(PlayerStateTracker.currentState)
    }

    override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback?) {
        this.callback = callback
        PlayerStateTracker.setListener(listener)
    }
}