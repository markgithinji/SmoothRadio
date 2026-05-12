package com.smoothradio.radio.core.domain.model

sealed class StreamStates(val label: String) {
    object PREPARING : StreamStates("Preparing Audio")
    object PLAYING : StreamStates("Playing")
    object BUFFERING : StreamStates("Buffering")
    object IDLE : StreamStates("Idle")
    object ENDED : StreamStates("Ended")
}
