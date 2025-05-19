package com.smoothradio.radio.testutil

import com.smoothradio.radio.service.StreamService

object PlayerStateTracker {

    interface Listener {
        fun onStateChanged(newState: String)
    }

    var currentState: String = StreamService.StreamStates.IDLE
        private set

    private var listener: Listener? = null

    fun setListener(l: Listener) {
        if (listener != null) {
            throw IllegalStateException("Callback has already been registered.")
        }
        listener = l
    }

    fun clearListener() {
        listener = null
    }

    fun updateState(newState: String) {
        currentState = newState
        listener?.onStateChanged(newState)
    }
}