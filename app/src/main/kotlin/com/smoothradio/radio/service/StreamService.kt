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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import com.google.android.gms.cast.framework.CastContext
import com.google.common.collect.ImmutableList
import com.smoothradio.radio.MainActivity
import com.smoothradio.radio.R
import com.smoothradio.radio.core.domain.repository.PlaybackStateRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * A background service that manages audio streaming using ExoPlayer and Media3 MediaSession.
 *
 */
@UnstableApi
@AndroidEntryPoint
class StreamService : MediaSessionService() {
    private var isPlaying = false
    private var stateChange = ""
    private var isPreparingForAd = false

    @Inject
    lateinit var player: ExoPlayer

    private lateinit var wrappedPlayer: Player

    @Inject
    lateinit var stateRepository: PlaybackStateRepository

    private var castPlayer: CastPlayer? = null
    private var castContext: CastContext? = null

    private lateinit var exoplayerEventListener: EventListener
    private var mediaSession: MediaSession? = null
    private lateinit var stopPlayFromTimerReceiver: StopPlayFromTimerReceiver
    private lateinit var setStopTimerReceiver: SetStopTimerReceiver
    private var notificationCallback: MediaNotification.Provider.Callback? = null

    private var currentStationName: String? = null
    private var currentStationLogo: Int = 0

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onCreate() {
        super.onCreate()
        setupWrappedPlayer()
        setupCastPlayer()
        exoplayerEventListener = EventListener()
        wrappedPlayer.addListener(exoplayerEventListener)
        setupMediaSession()
        setupNotificationChannel()
        registerTimerReceivers()
        setMediaNotificationProvider(CustomNotificationProvider())
    }

    private fun setupCastPlayer() {
        try {
            castContext = CastContext.getSharedInstance(this)
            castPlayer = CastPlayer(castContext!!)
            castPlayer?.setSessionAvailabilityListener(object : SessionAvailabilityListener {
                override fun onCastSessionAvailable() {
                    Log.d("StreamService", "Cast session available - switching player")
                    castPlayer?.addListener(exoplayerEventListener)
                    mediaSession?.player = castPlayer!!
                }

                override fun onCastSessionUnavailable() {
                    Log.d("StreamService", "Cast session unavailable - switching back to local player")
                    castPlayer?.removeListener(exoplayerEventListener)
                    mediaSession?.player = wrappedPlayer
                }
            })
        } catch (e: Exception) {
            Log.e("StreamService", "CastPlayer initialization failed", e)
        }
    }

    private fun setupWrappedPlayer() {
        wrappedPlayer = object : ForwardingPlayer(player) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .remove(Player.COMMAND_SEEK_TO_NEXT)
                    .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> false
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun getMediaMetadata(): MediaMetadata {
                val metadata = super.getMediaMetadata()
                val rawTitle = metadata.title?.toString() ?: ""
                val cleanedTitle = extractSongTitle(rawTitle)

                return metadata.buildUpon()
                    .setTitle(cleanedTitle)
                    .build()
            }
        }
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession.Builder(this, wrappedPlayer)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
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

    private fun createMediaStyleNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = Intent(this, StreamService::class.java).apply {
            action = if (player.isPlaying) ACTION_PAUSE else ACTION_PLAY
        }
        val playPausePendingIntent = PendingIntent.getService(
            this, 2, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, StreamService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 3, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val songTitle = stateRepository.metadata.value
        val title = when {
            isPlaying && songTitle.isNotEmpty() -> songTitle
            isPlaying -> "Playing"
            else -> stateChange.ifEmpty { "Preparing Audio" }
        }

        val stationDisplay = currentStationName ?: stateRepository.stationName.value ?: getString(R.string.app_name)

        Log.d("StreamService", "createNotification: title=$title, text=$stationDisplay")

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.notificationicon)
            .setContentTitle(title)
            .setContentText(stationDisplay)
            .setLargeIcon(getStationLogo())
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(player.isPlaying)
            .addAction(
                if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (player.isPlaying) "Pause" else "Play",
                playPausePendingIntent
            )
            .addAction(
                R.drawable.ic_stop, "Stop",
                stopPendingIntent
            )
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
        } else {
            null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { handleIntent(it) }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action ?: return // Ignore null action (fixes log flood)

        val name = intent.getStringExtra(EXTRA_STATION_NAME)
        if (name != null) {
            currentStationName = name
            stateRepository.updateStationName(name)
        }

        val logo = intent.getIntExtra(EXTRA_LOGO, 0)
        if (logo != 0) {
            currentStationLogo = logo
        }

        val link = intent.getStringExtra(EXTRA_LINK) ?: ""

        updateNotificationInternal()

        when (action) {
            ACTION_START -> {
                Log.d("StreamService", "🎵 ACTION_START → ${currentStationName}")
                isPreparingForAd = false
                setState(StreamStates.PREPARING)
                play(link)
            }

            ACTION_SHOW_AD -> {
                Log.d("StreamService", "📢 ACTION_SHOW_AD → ${currentStationName}")
                isPreparingForAd = true
                setState(StreamStates.PREPARING)
                prepareShowAd()
            }

            ACTION_STOP -> {
                Log.d("StreamService", "🛑 ACTION_STOP")
                isPreparingForAd = false
                player.pause()
                player.stop()
                player.clearMediaItems()
                setState(StreamStates.IDLE)
                stopSelf()
            }

            ACTION_PLAY -> player.play()
            ACTION_PAUSE -> player.pause()
        }
    }

    private fun setState(newState: String) {
        if (stateChange == newState) return

        Log.d("StreamService", "  → state: $newState")
        stateChange = newState
        stateRepository.updateState(newState)
        updateNotificationInternal()
    }

    private fun updateNotificationInternal() {
        notificationCallback?.onNotificationChanged(MediaNotification(NOTIFICATION_ID, createMediaStyleNotification()))
    }

    private fun registerTimerReceivers() {
        stopPlayFromTimerReceiver = StopPlayFromTimerReceiver()
        setStopTimerReceiver = SetStopTimerReceiver()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopPlayFromTimerReceiver, IntentFilter(ACTION_STOP_FROM_TIMER), RECEIVER_NOT_EXPORTED)
            registerReceiver(setStopTimerReceiver, IntentFilter(ACTION_SET_TIMER), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stopPlayFromTimerReceiver, IntentFilter(ACTION_STOP_FROM_TIMER))
            registerReceiver(setStopTimerReceiver, IntentFilter(ACTION_SET_TIMER))
        }
    }

    override fun onDestroy() {
        cleanupResources()
        Log.d("StreamService", "onDestroy")
        super.onDestroy()
    }

    private fun cleanupResources() {
        mediaSession?.run {
            wrappedPlayer.removeListener(exoplayerEventListener)
            player.release()
            castPlayer?.release()
            release()
            mediaSession = null
        }
        isPlaying = false
        stateChange = ""
        isPreparingForAd = false
        unregisterTimerReceivers()
    }

    private fun unregisterTimerReceivers() {
        try {
            unregisterReceiver(stopPlayFromTimerReceiver)
            unregisterReceiver(setStopTimerReceiver)
        } catch (e: Exception) { }
    }

    private fun play(link: String) {
        isPreparingForAd = false
        preparePlayer(link.toUri())
        player.play()
    }

    private fun preparePlayer(uri: Uri) {
        player.stop()
        val mediaItem = MediaItem.fromUri(uri)
        player.setMediaItem(mediaItem)
        player.prepare()
    }

    private fun prepareShowAd() {
        player.stop()
        isPlaying = false
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

        override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean = false
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
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            val rawTitle = mediaMetadata.title?.toString() ?: ""
            val cleaned = extractSongTitle(rawTitle)

            if (stateRepository.metadata.value != cleaned) {
                stateRepository.updateMetadata(cleaned)
                updateNotificationInternal()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@StreamService.isPlaying = isPlaying
            updateNotificationInternal()
            if (isPreparingForAd) return

            val newState = if (isPlaying) StreamStates.PLAYING
            else if (player.playbackState == Player.STATE_READY) StreamStates.IDLE
            else return

            Log.d("StreamService", "  → onIsPlayingChanged: $newState")
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
                Player.STATE_IDLE -> { isPlaying = false; StreamStates.IDLE }
                Player.STATE_READY -> if (player.isPlaying) StreamStates.PLAYING else return
                Player.STATE_ENDED -> { isPlaying = false; StreamStates.ENDED }
                else -> return
            }

            Log.d("StreamService", "  → playbackState: $newState")
            setState(newState)
        }
    }

    private fun extractSongTitle(rawTitle: String): String {
        val trimmed = rawTitle.trim()
        if (trimmed.contains("<LogEvent") && trimmed.contains("Type=\"SONG\"")) {
            return try {
                val songPattern = Regex(
                    """<LogEvent[^>]*Type="SONG"[^>]*LastStarted="true"[^>]*>.*?<Asset[^>]*Title="([^"]*)"[^>]*Artist1="([^"]*)"[^>]*/>.*?</LogEvent>""",
                    RegexOption.DOT_MATCHES_ALL
                )
                val match = songPattern.find(trimmed)
                if (match != null) {
                    val title = match.groupValues[1].replace("&amp;", "&").trim()
                    val artist = match.groupValues[2].replace("&amp;(?!#?\\w+;)", "&").trim()
                    if (title.isNotEmpty()) "$title - $artist" else ""
                } else {
                    val fallbackPattern = Regex("""<LogEvent[^>]*Type="SONG"[^>]*>.*?<Asset[^>]*Title="([^"]*)"[^>]*/>""")
                    val fallbackMatch = fallbackPattern.find(trimmed)
                    fallbackMatch?.groupValues?.get(1)?.replace("&amp;", "&")?.trim() ?: ""
                }
            } catch (e: Exception) { "" }
        }
        val cleanTitle = trimmed.replace(Regex("<[^>]*>"), "").replace("\n", " ").replace("\r", "").replace("\\s+".toRegex(), " ").trim()
        return if (cleanTitle.isNotEmpty() && cleanTitle != "-") cleanTitle else ""
    }

    object StreamStates {
        const val PREPARING = "Preparing Audio"
        const val PLAYING = "Playing"
        const val BUFFERING = "Buffering"
        const val IDLE = "Idle"
        const val ENDED = "Ended"
    }

    companion object {
        const val ACTION_START = "SmoothService:Start"
        const val ACTION_STOP = "SmoothService:Stop"
        const val ACTION_PLAY = "SmoothService:Play"
        const val ACTION_PAUSE = "SmoothService:Pause"
        const val ACTION_SHOW_AD = "SmoothService:ShowAd"
        const val ACTION_SET_TIMER = "SmoothService:SetTimer"
        private const val ACTION_STOP_FROM_TIMER = "SmoothService:StopFromTimer"
        const val EXTRA_STREAM_STATE = "STREAM_STATE"
        private const val NOTIFICATION_ID = 1
        const val EXTRA_STATE = "state"
        const val EXTRA_TIME_IN_MILLIS = "timeInMillis"
        const val EXTRA_LOGO = "logo"
        const val EXTRA_STATION_NAME = "stationName"
        const val EXTRA_LINK = "url"
        private const val CHANNEL_ID = "media_playback_channel"
    }
}