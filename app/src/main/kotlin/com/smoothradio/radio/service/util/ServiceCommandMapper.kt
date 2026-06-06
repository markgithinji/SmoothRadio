package com.smoothradio.radio.service.util

import android.content.Intent
import com.smoothradio.radio.service.StreamService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServiceCommandMapper @Inject constructor() {
    fun map(intent: Intent): ServiceCommand {
        val action = intent.action ?: return ServiceCommand.None

        return when (action) {
            StreamService.ACTION_START -> ServiceCommand.Start(
                link = intent.getStringExtra(StreamService.EXTRA_LINK) ?: "",
                name = intent.getStringExtra(StreamService.EXTRA_STATION_NAME),
                logo = intent.getIntExtra(StreamService.EXTRA_LOGO, 0)
            )

            StreamService.ACTION_SHOW_AD -> ServiceCommand.ShowAd(
                link = intent.getStringExtra(StreamService.EXTRA_LINK) ?: "",
                name = intent.getStringExtra(StreamService.EXTRA_STATION_NAME),
                logo = intent.getIntExtra(StreamService.EXTRA_LOGO, 0)
            )

            StreamService.ACTION_STOP -> ServiceCommand.Stop
            StreamService.ACTION_PLAY -> ServiceCommand.Play
            StreamService.ACTION_PAUSE -> ServiceCommand.Pause
            StreamService.ACTION_SEEK_BACK -> ServiceCommand.SeekBack
            StreamService.ACTION_SEEK_FORWARD -> ServiceCommand.SeekForward
            StreamService.ACTION_SEEK_TO -> ServiceCommand.SeekTo(
                intent.getLongExtra(StreamService.EXTRA_POSITION, 0L)
            )

            StreamService.ACTION_SET_EQ_BAND -> ServiceCommand.SetEqBand(
                band = intent.getIntExtra(StreamService.EXTRA_BAND, -1),
                level = intent.getShortExtra(StreamService.EXTRA_LEVEL, 0)
            )

            else -> ServiceCommand.None
        }
    }
}
