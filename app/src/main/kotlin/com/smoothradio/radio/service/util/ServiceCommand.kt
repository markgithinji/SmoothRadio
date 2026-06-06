package com.smoothradio.radio.service.util

sealed class ServiceCommand {
    data class Start(val link: String, val name: String?, val logo: Int) : ServiceCommand()
    data class ShowAd(val link: String, val name: String?, val logo: Int) : ServiceCommand()
    object Stop : ServiceCommand()
    object Play : ServiceCommand()
    object Pause : ServiceCommand()
    object SeekBack : ServiceCommand()
    object SeekForward : ServiceCommand()
    data class SeekTo(val position: Long) : ServiceCommand()
    data class SetEqBand(val band: Int, val level: Short) : ServiceCommand()
    object None : ServiceCommand()
}
