package com.smoothradio.radio.service.util.command

import android.content.Intent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServiceCommandMapper @Inject constructor() {
    fun map(intent: Intent): ServiceCommand {
        val action = intent.action ?: return ServiceCommand.None

        return when (action) {
            ServiceCommand.ACTION_START -> ServiceCommand.Start(
                link = intent.getStringExtra(ServiceCommand.EXTRA_LINK) ?: "",
                name = intent.getStringExtra(ServiceCommand.EXTRA_STATION_NAME),
                logo = intent.getIntExtra(ServiceCommand.EXTRA_LOGO, 0)
            )

            ServiceCommand.ACTION_SHOW_AD -> ServiceCommand.ShowAd(
                link = intent.getStringExtra(ServiceCommand.EXTRA_LINK) ?: "",
                name = intent.getStringExtra(ServiceCommand.EXTRA_STATION_NAME),
                logo = intent.getIntExtra(ServiceCommand.EXTRA_LOGO, 0)
            )

            ServiceCommand.ACTION_STOP -> ServiceCommand.Stop
            ServiceCommand.ACTION_PLAY -> ServiceCommand.Play
            ServiceCommand.ACTION_PAUSE -> ServiceCommand.Pause
            ServiceCommand.ACTION_SEEK_BACK -> ServiceCommand.SeekBack
            ServiceCommand.ACTION_SEEK_FORWARD -> ServiceCommand.SeekForward
            ServiceCommand.ACTION_SEEK_TO -> ServiceCommand.SeekTo(
                intent.getLongExtra(ServiceCommand.EXTRA_POSITION, 0L)
            )

            ServiceCommand.ACTION_SET_EQ_BAND -> ServiceCommand.SetEqBand(
                band = intent.getIntExtra(ServiceCommand.EXTRA_BAND, -1),
                level = intent.getShortExtra(ServiceCommand.EXTRA_LEVEL, 0)
            )

            else -> ServiceCommand.None
        }
    }
}
