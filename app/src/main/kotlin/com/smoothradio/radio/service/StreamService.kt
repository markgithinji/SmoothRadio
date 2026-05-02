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
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.smoothradio.radio.MainActivity
import com.smoothradio.radio.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * A background service that manages audio streaming using ExoPlayer and Media3 MediaSession.
 *
 */
@UnstableApi
@AndroidEntryPoint
class StreamService : MediaSessionService() {
    // Playback State
    private var isPlaying = false
    private var stateChange = ""

    @Inject
    lateinit var player: ExoPlayer

    private lateinit var exoplayerEventListener: EventListener

    // Media Session
    private var mediaSession: MediaSession? = null

    // Timer Receivers
    private lateinit var stopPlayFromTimerReceiver: StopPlayFromTimerReceiver
    private lateinit var setStopTimerReceiver: SetStopTimerReceiver

    private var isForegroundStarted = false
    private var currentStationName: String? = null
    private var currentStationLogo: Int = 0

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onCreate() {
        super.onCreate()
        exoplayerEventListener = EventListener()
        player.addListener(exoplayerEventListener)
        setupMediaSession()
        setupNotificationChannel()
        registerTimerReceivers()

        startForeground(NOTIFICATION_ID, createMediaStyleNotification())
        isForegroundStarted = true
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    // Send current state to newly connected controller
                    sendStateToSession(stateChange)
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
                }
            })
            .build()
    }

    /**
     * Updates the MediaSession custom layout to signal state changes to all connected controllers.
     * The display name of the first command button carries the stream state string.
     * This is synchronous and immediate - no async delay.
     */
    private fun sendStateToSession(state: String) {
        mediaSession?.setCustomLayout(
            listOf(
                CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                    .setDisplayName(state)
                    .setEnabled(false)
                    .build()
            )
        )
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.notificationicon)
            .setContentTitle(currentStationName ?: getString(R.string.app_name))
            .setContentText(stateChange.ifEmpty { getString(R.string.preparing) })
            .setLargeIcon(getStationLogo())
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(isPlaying)
            .setStyle(
                mediaSession?.let {
                    androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(it)
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

    private fun updateMediaStyleNotification() {
        if (!isForegroundStarted) return

        val notification = createMediaStyleNotification()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        intent?.let { handleIntent(it) }
        return START_STICKY
    }

    private fun handleIntent(intent: Intent) {
        // Store station info for later updates
        currentStationName = intent.getStringExtra(EXTRA_STATION_NAME)
        currentStationLogo = intent.getIntExtra(EXTRA_LOGO, 0)

        val link = intent.getStringExtra(EXTRA_LINK) ?: ""

        when (intent.action) {
            ACTION_START -> {
                // Signal PREPARING state IMMEDIATELY before player operations
                setState(StreamStates.PREPARING)
                updateMediaStyleNotification()
                play(link)
            }

            ACTION_SHOW_AD -> {
                // Signal PREPARING state IMMEDIATELY before player operations
                setState(StreamStates.PREPARING)
                updateMediaStyleNotification()
                prepareShowAd()
            }

            ACTION_STOP -> {
                setState(StreamStates.IDLE)
                stopSelf()
            }

            else -> {
                // Ignore unknown actions
            }
        }
    }

    /**
     * Centralized state setter that updates both local state and MediaSession.
     * Called synchronously so UI gets the update before player operations begin.
     */
    private fun setState(newState: String) {
        stateChange = newState
        sendStateToSession(newState)
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
        super.onDestroy()
    }

    private fun cleanupResources() {
        mediaSession?.run {
            player.removeListener(exoplayerEventListener)
            player.release()
            release()
            mediaSession = null
        }
        isPlaying = false
        stateChange = ""
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancelAll()
        unregisterTimerReceivers()
    }

    private fun unregisterTimerReceivers() {
        try {
            unregisterReceiver(stopPlayFromTimerReceiver)
            unregisterReceiver(setStopTimerReceiver)
        } catch (e: Exception) {
            // Receivers might not be registered
        }
    }

    private fun play(link: String) {
        preparePlayer(link.toUri())
        player.play()
        updateMediaStyleNotification()
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
        updateMediaStyleNotification()
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
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@StreamService.isPlaying = isPlaying
            if (isPlaying) {
                setState(StreamStates.PLAYING)
            } else if (player.playbackState == Player.STATE_READY) {
                setState(StreamStates.IDLE)
            }
            updateMediaStyleNotification()
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
            when (state) {
                Player.STATE_BUFFERING -> {
                    setState(StreamStates.BUFFERING)
                    updateMediaStyleNotification()
                }
                Player.STATE_IDLE -> {
                    setState(StreamStates.IDLE)
                    isPlaying = false
                    updateMediaStyleNotification()
                }
                Player.STATE_READY -> {
                    if (player.isPlaying) {
                        setState(StreamStates.PLAYING)
                        updateMediaStyleNotification()
                    }
                }
                Player.STATE_ENDED -> {
                    setState(StreamStates.ENDED)
                    isPlaying = false
                    updateMediaStyleNotification()
                }
            }
        }
    }

    object StreamStates {
        const val PREPARING = "Preparing Audio"
        const val PLAYING = "Playing"
        const val BUFFERING = "Buffering"
        const val IDLE = "Idle"
        const val ENDED = "Ended"
    }

    companion object {
        // Service Actions
        const val ACTION_START = "SmoothService:Start"
        const val ACTION_STOP = "SmoothService:Stop"
        const val ACTION_SHOW_AD = "SmoothService:ShowAd"

        // Timer Actions (system-level)
        const val ACTION_SET_TIMER = "SmoothService:SetTimer"
        private const val ACTION_STOP_FROM_TIMER = "SmoothService:StopFromTimer"

        // Session Extra Keys
        const val EXTRA_STREAM_STATE = "STREAM_STATE"

        private const val NOTIFICATION_ID = 1

        // Broadcast Extras
        const val EXTRA_STATE = "state"
        const val EXTRA_TIME_IN_MILLIS = "timeInMillis"
        const val EXTRA_LOGO = "logo"
        const val EXTRA_STATION_NAME = "stationName"
        const val EXTRA_LINK = "url"

        // Notification Info
        private const val CHANNEL_ID = "media_playback_channel"
    }
}