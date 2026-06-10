package com.smoothradio.radio.core.domain.model

sealed class StreamStates(val label: String) {
    object PREPARING : StreamStates("PREPARING AUDIO")
    object PLAYING : StreamStates("PLAYING")
    object PAUSED : StreamStates("PAUSED")
    object BUFFERING : StreamStates("BUFFERING")
    object IDLE : StreamStates("IDLE")
    object ENDED : StreamStates("ENDED")
}
