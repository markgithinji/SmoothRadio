@file:OptIn(UnstableApi::class)

package com.smoothradio.radio.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.cast.CastPlayer
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.collect.ImmutableList
import com.smoothradio.radio.MainActivity
import com.smoothradio.radio.R
import com.smoothradio.radio.core.domain.model.StreamStates
import com.smoothradio.radio.core.domain.repository.EqualizerRepository
import com.smoothradio.radio.core.domain.repository.PlaybackStateRepository
import com.smoothradio.radio.service.MetadataUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A background service that manages audio streaming using ExoPlayer and Media3 MediaSession.
 *
 */
@AndroidEntryPoint
class StreamService : MediaSessionService() {

    private var isPlaying = false
    private var stateChange: StreamStates = StreamStates.IDLE
    private var isPreparingForAd = false

    private var currentStationName: String? = null
    private var currentStationLogo: Int = 0
    private var currentSongTitle: String = ""

    @Inject
    lateinit var player: ExoPlayer
    @Inject
    lateinit var stateRepository: PlaybackStateRepository
    @Inject
    lateinit var equalizerRepository: EqualizerRepository
    @Inject
    @JvmField
    var castPlayer: CastPlayer? = null

    private lateinit var wrappedPlayer: Player
    private lateinit var exoplayerEventListener: EventListener
    private var mediaSession: MediaSession? = null
    private var notificationCallback: MediaNotification.Provider.Callback? = null

    private var equalizer: Equalizer? = null
    private var audioSessionId: Int = 0
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var stopPlayFromTimerReceiver: StopPlayFromTimerReceiver
    private lateinit var setStopTimerReceiver: SetStopTimerReceiver

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onCreate() {
        super.onCreate()
        setupWrappedPlayer()
        exoplayerEventListener = EventListener()
        wrappedPlayer.addListener(exoplayerEventListener)
        setupMediaSession()
        setupNotificationChannel()
        registerTimerReceivers()
        setMediaNotificationProvider(CustomNotificationProvider())
    }

    private fun setupWrappedPlayer() {
        val basePlayer = castPlayer ?: player
        wrappedPlayer = object : ForwardingPlayer(basePlayer) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .remove(COMMAND_SEEK_TO_NEXT)
                    .remove(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .remove(COMMAND_SEEK_TO_PREVIOUS)
                    .remove(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    COMMAND_SEEK_TO_NEXT,
                    COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    COMMAND_SEEK_TO_PREVIOUS,
                    COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> false

                    else -> super.isCommandAvailable(command)
                }
            }

            override fun getMediaMetadata(): MediaMetadata {
                val metadata = super.getMediaMetadata()
                val rawTitle = metadata.title?.toString() ?: ""
                val cleanedTitle = MetadataUtils.extractSongTitle(rawTitle)
                return metadata.buildUpon().setTitle(cleanedTitle).build()
            }
        }
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession.Builder(this, wrappedPlayer)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    private fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun registerTimerReceivers() {
        stopPlayFromTimerReceiver = StopPlayFromTimerReceiver()
        setStopTimerReceiver = SetStopTimerReceiver()
        ContextCompat.registerReceiver(
            this,
            stopPlayFromTimerReceiver,
            IntentFilter(ACTION_STOP_FROM_TIMER),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ContextCompat.registerReceiver(
            this,
            setStopTimerReceiver,
            IntentFilter(ACTION_SET_TIMER),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun createMediaStyleNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = Intent(this, StreamService::class.java).apply {
            action = if (wrappedPlayer.isPlaying) ACTION_PAUSE else ACTION_PLAY
        }
        val playPausePendingIntent = PendingIntent.getService(
            this, 2, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, StreamService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 3, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when {
            isPlaying && currentSongTitle.isNotEmpty() -> currentSongTitle
            isPlaying -> getString(R.string.player_playing)
            else -> stateChange.label.ifEmpty { getString(R.string.player_preparing_audio) }
        }

        val stationDisplay = currentStationName ?: getString(R.string.app_name)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.notificationicon)
            .setContentTitle(title)
            .setContentText(stationDisplay)
            .setLargeIcon(getStationLogo())
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(wrappedPlayer.isPlaying)
            .addAction(
                if (wrappedPlayer.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (wrappedPlayer.isPlaying) getString(R.string.player_pause) else getString(R.string.player_play),
                playPausePendingIntent
            )
            .addAction(R.drawable.ic_stop, getString(R.string.player_stop), stopPendingIntent)
            .setStyle(
                mediaSession?.let {
                    androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(it)
                        .setShowActionsInCompactView(0, 1)
                }
            )
            .build()
    }

    private fun getStationLogo(): Bitmap? {
        return if (currentStationLogo != 0) {
            BitmapFactory.decodeResource(resources, currentStationLogo)
        } else null
    }

    private fun updateNotificationInternal() {
        notificationCallback?.onNotificationChanged(
            MediaNotification(NOTIFICATION_ID, createMediaStyleNotification())
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { handleIntent(it) }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action ?: return

        val name = intent.getStringExtra(EXTRA_STATION_NAME)
        if (name != null) {
            currentStationName = name
            stateRepository.updateStationName(name)
        }

        val logo = intent.getIntExtra(EXTRA_LOGO, 0)
        if (logo != 0) currentStationLogo = logo

        val link = intent.getStringExtra(EXTRA_LINK) ?: ""

        updateNotificationInternal()

        when (action) {
            ACTION_START -> {
                Log.d("StreamService", " ACTION_START → ${currentStationName}")
                isPreparingForAd = false
                setState(StreamStates.PREPARING)
                play(link)
            }

            ACTION_SHOW_AD -> {
                Log.d("StreamService", " ACTION_SHOW_AD → ${currentStationName}")
                isPreparingForAd = true
                setState(StreamStates.PREPARING)
                prepareShowAd()
            }

            ACTION_STOP -> {
                Log.d("StreamService", " ACTION_STOP")
                isPreparingForAd = false
                wrappedPlayer.pause()
                wrappedPlayer.stop()
                wrappedPlayer.clearMediaItems()
                setState(StreamStates.IDLE)
                stopSelf()
            }

            ACTION_PLAY -> wrappedPlayer.play()
            ACTION_PAUSE -> wrappedPlayer.pause()
            ACTION_SET_EQ_BAND -> {
                val band = intent.getIntExtra(EXTRA_BAND, -1)
                val level = intent.getShortExtra(EXTRA_LEVEL, 0)
                if (band != -1) setEqualizerBand(band, level)
            }
        }
    }

    private fun setState(newState: StreamStates) {
        if (stateChange == newState) return
        Log.d("StreamService", "  → state: ${newState.label}")
        stateChange = newState
        stateRepository.updateState(newState)
        updateNotificationInternal()
    }

    private fun play(link: String) {
        isPreparingForAd = false
        preparePlayer(link.toUri())
        wrappedPlayer.play()
    }

    private fun preparePlayer(uri: Uri) {
        wrappedPlayer.stop()
        val mediaItem = MediaItem.fromUri(uri)
        wrappedPlayer.setMediaItem(mediaItem)
        wrappedPlayer.prepare()
    }

    private fun prepareShowAd() {
        wrappedPlayer.stop()
        isPlaying = false
    }

    private fun setupEqualizer(sessionId: Int) {
        if (sessionId == 0 || sessionId == audioSessionId) return
        audioSessionId = sessionId
        try {
            equalizer?.release()
            equalizer = Equalizer(0, sessionId).apply {
                enabled = true
                val bands = numberOfBands
                serviceScope.launch {
                    for (i in 0 until bands) {
                        val level = equalizerRepository.getBandLevel(i)
                        if (level != 0.toShort()) {
                            try {
                                setBandLevel(i.toShort(), level)
                            } catch (e: Exception) {
                                Log.e("StreamService", "Failed to apply EQ band $i", e)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("StreamService", "Failed to initialize Equalizer", e)
        }
    }

    private fun setEqualizerBand(band: Int, level: Short) {
        try {
            equalizer?.setBandLevel(band.toShort(), level)
        } catch (e: Exception) {
            Log.e("StreamService", "Failed to set EQ band $band", e)
        }
    }

    private fun unregisterTimerReceivers() {
        try {
            unregisterReceiver(stopPlayFromTimerReceiver)
            unregisterReceiver(setStopTimerReceiver)
        } catch (e: Exception) {
        }
    }

    override fun onDestroy() {
        cleanupResources()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun cleanupResources() {
        equalizer?.release()
        equalizer = null
        wrappedPlayer.removeListener(exoplayerEventListener)
        wrappedPlayer.release()
        mediaSession?.release()
        mediaSession = null
        isPlaying = false
        stateChange = StreamStates.IDLE
        isPreparingForAd = false
        unregisterTimerReceivers()
    }


    private inner class CustomNotificationProvider : MediaNotification.Provider {
        override fun createNotification(
            session: MediaSession,
            customLayout: ImmutableList<CommandButton>,
            actionFactory: MediaNotification.ActionFactory,
            onNotificationChangedCallback: MediaNotification.Provider.Callback
        ): MediaNotification {
            notificationCallback = onNotificationChangedCallback
            return MediaNotification(NOTIFICATION_ID, createMediaStyleNotification())
        }

        override fun handleCustomCommand(
            session: MediaSession,
            action: String,
            extras: Bundle
        ): Boolean = false

        override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo {
            return MediaNotification.Provider.NotificationChannelInfo(
                CHANNEL_ID,
                getString(R.string.notification_channel_name)
            )
        }
    }

    inner class StopPlayFromTimerReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Toast.makeText(context, getString(R.string.stopped), Toast.LENGTH_SHORT).show()
            stopSelf()
        }
    }

    inner class SetStopTimerReceiver : BroadcastReceiver() {
        private val stopPlayFromTimerIntent = Intent(ACTION_STOP_FROM_TIMER).setPackage(packageName)
        override fun onReceive(context: Context, intent: Intent) {
            val timeInMillis = intent.getLongExtra(EXTRA_TIME_IN_MILLIS, 0)
            val alarmPendingIntent = PendingIntent.getBroadcast(
                this@StreamService,
                0,
                stopPlayFromTimerIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, timeInMillis, alarmPendingIntent)
        }
    }

    inner class EventListener : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            setupEqualizer(audioSessionId)
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            val rawTitle = mediaMetadata.title?.toString() ?: ""
            val cleaned = MetadataUtils.extractSongTitle(rawTitle)
            if (currentSongTitle != cleaned) {
                currentSongTitle = cleaned
                stateRepository.updateMetadata(cleaned)
                updateNotificationInternal()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@StreamService.isPlaying = isPlaying
            updateNotificationInternal()
            if (isPreparingForAd) return
            val newState = if (isPlaying) StreamStates.PLAYING
            else if (wrappedPlayer.playbackState == Player.STATE_READY) StreamStates.IDLE
            else return
            setState(newState)
        }

        override fun onPlayerError(error: PlaybackException) {
            val message = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> getString(R.string.toast_station_unreachable)

                else -> getString(R.string.toast_unexpected_error)
            }
            Toast.makeText(this@StreamService, message, Toast.LENGTH_SHORT).show()
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (isPreparingForAd) return
            val newState = when (state) {
                Player.STATE_BUFFERING -> StreamStates.BUFFERING
                Player.STATE_IDLE -> StreamStates.IDLE
                Player.STATE_READY -> if (wrappedPlayer.isPlaying) StreamStates.PLAYING else return
                Player.STATE_ENDED -> StreamStates.ENDED
                else -> return
            }
            setState(newState)
        }
    }

    companion object {
        const val ACTION_START = "SmoothService:Start"
        const val ACTION_STOP = "SmoothService:Stop"
        const val ACTION_PLAY = "SmoothService:Play"
        const val ACTION_PAUSE = "SmoothService:Pause"
        const val ACTION_SHOW_AD = "SmoothService:ShowAd"
        const val ACTION_SET_TIMER = "SmoothService:SetTimer"
        const val ACTION_SET_EQ_BAND = "SmoothService:SetEqBand"
        private const val ACTION_STOP_FROM_TIMER = "SmoothService:StopFromTimer"
        private const val NOTIFICATION_ID = 1
        const val EXTRA_STATE = "state"
        const val EXTRA_TIME_IN_MILLIS = "timeInMillis"
        const val EXTRA_LOGO = "logo"
        const val EXTRA_STATION_NAME = "stationName"
        const val EXTRA_LINK = "url"
        const val EXTRA_BAND = "band"
        const val EXTRA_LEVEL = "level"
        private const val CHANNEL_ID = "media_playback_channel"
    }
}
