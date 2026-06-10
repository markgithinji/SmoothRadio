package com.smoothradio.radio.service.util.proxy

sealed class ProxyState {
    object Idle : ProxyState()
    object Connecting : ProxyState()
    data class Streaming(val mimeType: String?, val bitrate: String?) : ProxyState()
}
