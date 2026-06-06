package com.smoothradio.radio.service.util.command

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

    companion object {
        const val ACTION_START = "SmoothService:Start"
        const val ACTION_STOP = "SmoothService:Stop"
        const val ACTION_PLAY = "SmoothService:Play"
        const val ACTION_PAUSE = "SmoothService:Pause"
        const val ACTION_SEEK_BACK = "SmoothService:SeekBack"
        const val ACTION_SEEK_FORWARD = "SmoothService:SeekForward"
        const val ACTION_SEEK_TO = "SmoothService:SeekTo"
        const val ACTION_SHOW_AD = "SmoothService:ShowAd"
        const val ACTION_SET_TIMER = "SmoothService:SetTimer"
        const val ACTION_STOP_FROM_TIMER = "SmoothService:StopFromTimer"
        const val ACTION_SET_EQ_BAND = "SmoothService:SetEqBand"
        const val COMMAND_SET_EQ_BAND = "SET_EQ_BAND"
        const val COMMAND_SET_SLEEP_TIMER = "SET_SLEEP_TIMER"
        
        const val EXTRA_TIME_IN_MILLIS = "timeInMillis"
        const val EXTRA_LOGO = "logo"
        const val EXTRA_STATION_NAME = "stationName"
        const val EXTRA_LINK = "url"
        const val EXTRA_POSITION = "position"
        const val EXTRA_BAND = "band"
        const val EXTRA_LEVEL = "level"
    }
}
