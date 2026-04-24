package com.smoothradio.radio.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.smoothradio.radio.MainActivity
import com.smoothradio.radio.R

/**
 * A background service that manages audio streaming using ExoPlayer.
 *
 * This service handles playback, pause/resume, stop, audio focus management,
 * notifications, and broadcast communication with the UI. It supports playing
 * audio streams from URLs and responds to various actions triggered by the UI or system events.
 */
class StreamService : Service() {
    // Playback State
    private var isPlaying = false
    private var stateChange = ""

    // Playback Components
    private var player: ExoPlayer? = null
    private var mediaMetadataOR: MediaMetadata? = null
    private lateinit var exoplayerEventListener: EventListener

    // System Services
    private lateinit var audioManager: AudioManager
    private lateinit var onAudioFocusChangeListener: AudioManager.OnAudioFocusChangeListener

    // Broadcast Intents
    private lateinit var eventIntent: Intent
    private lateinit var metadataIntent: Intent
    private lateinit var playPauseIntent: Intent

    // Media Session
    private lateinit var mediaSession: MediaSessionCompat

    // Notification Components
    private lateinit var stopPI: PendingIntent
    private lateinit var playPausePI: PendingIntent

    // Broadcast Receivers
    private lateinit var stopPlayFromTimerReceiver: StopPlayFromTimerReceiver
    private lateinit var setStopTimerReceiver: SetStopTimerReceiver
    private lateinit var restoreUIReceiver: RestoreUIReceiver
    private lateinit var pLayPauseReceiver: PLayPauseReceiver
    private lateinit var requestFocusReceiver: RequestFocusReceiver

    private var isForegroundStarted = false
    private var currentStationName: String? = null
    private var currentStationLogo: Int = 0

    // === Lifecycle Methods ===

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        setupAudioFocus()
        setupIntents()
        setupMediaSession()
        setupNotificationChannel()
        registerReceivers()
        exoplayerEventListener = EventListener()

        startForeground(NOTIFICATION_ID, createMediaStyleNotification())
        isForegroundStarted = true
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "StreamService")
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )

        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                if (!isPlaying) {
                    resumePlayer()
                }
            }

            override fun onPause() {
                if (isPlaying) {
                    pausePlayer(null)
                }
            }

            override fun onStop() {
                stopSelf()
            }
        })

        mediaSession.isActive = true
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

        playPausePI = getPlayPausePendingIntent()
        stopPI = getStopPendingIntent()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.notificationicon)
            .setContentTitle(currentStationName ?: getString(R.string.app_name))
            .setContentText(stateChange.ifEmpty { getString(R.string.preparing) })
            .setLargeIcon(getStationLogo())
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(isPlaying)
            .addAction(
                if (isPlaying) R.drawable.pause_notification_icon else R.drawable.play_notification_icon,
                if (isPlaying) TITLE_PAUSE else TITLE_PLAY,
                playPausePI
            )
            .addAction(R.drawable.stop_notification_icon, TITLE_STOP, stopPI)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0)
            )
            .build()
    }

    private fun getPlayPausePendingIntent(): PendingIntent {
        val intent = Intent(ACTION_PLAY_PAUSE).setPackage(packageName)
        return PendingIntent.getBroadcast(
            this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getStopPendingIntent(): PendingIntent {
        val intent = Intent(ACTION_STOP).setPackage(packageName)
        return PendingIntent.getBroadcast(
            this, 2, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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

        playPausePI = getPlayPausePendingIntent()
        stopPI = getStopPendingIntent()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.notificationicon)
            .setContentTitle(currentStationName ?: getString(R.string.app_name))
            .setContentText(stateChange)
            .setLargeIcon(getStationLogo())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(isPlaying)
            .addAction(
                if (isPlaying) R.drawable.pause_notification_icon else R.drawable.play_notification_icon,
                if (isPlaying) TITLE_PAUSE else TITLE_PLAY,
                playPausePI
            )
            .addAction(R.drawable.stop_notification_icon, TITLE_STOP, stopPI)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0)
            )
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { handleIntent(it) }
        return START_NOT_STICKY
    }

    private fun handleIntent(intent: Intent) {
        // Store station info for later updates
        currentStationName = intent.getStringExtra(EXTRA_STATION_NAME)
        currentStationLogo = intent.getIntExtra(EXTRA_LOGO, 0)

        val link = intent.getStringExtra(EXTRA_LINK) ?: ""

        when (intent.action) {
            ACTION_START -> {
                updateMediaStyleNotification()
                play(link)
            }

            ACTION_SHOW_AD -> {
                updateMediaStyleNotification()
                prepareShowAd()
            }

            else -> throw IllegalArgumentException("Unexpected action received: ${intent.action}")
        }
    }

    private fun setupAudioFocus() {
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        playPauseIntent = Intent()
        onAudioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_GAIN -> player?.play()
                AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    playPauseIntent.putExtra(EXTRA_SOURCE, "audioFocus")
                    sendBroadcast(playPauseIntent)
                }
            }
        }
    }

    private fun setupIntents() {
        eventIntent = Intent(ACTION_EVENT_CHANGE).setPackage(packageName)
        metadataIntent = Intent(ACTION_METADATA_CHANGE).setPackage(packageName)
    }

    private fun registerReceivers() {
        stopPlayFromTimerReceiver = StopPlayFromTimerReceiver()
        setStopTimerReceiver = SetStopTimerReceiver()
        restoreUIReceiver = RestoreUIReceiver()
        pLayPauseReceiver = PLayPauseReceiver()
        requestFocusReceiver = RequestFocusReceiver()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                stopPlayFromTimerReceiver,
                IntentFilter(ACTION_STOP),
                RECEIVER_NOT_EXPORTED
            )
            registerReceiver(
                restoreUIReceiver,
                IntentFilter(ACTION_GET_STATE),
                RECEIVER_NOT_EXPORTED
            )
            registerReceiver(
                setStopTimerReceiver,
                IntentFilter(ACTION_SET_TIMER),
                RECEIVER_NOT_EXPORTED
            )
            registerReceiver(
                pLayPauseReceiver,
                IntentFilter(ACTION_PLAY_PAUSE),
                RECEIVER_NOT_EXPORTED
            )
            registerReceiver(
                requestFocusReceiver,
                IntentFilter(ACTION_REQUEST_AUDIO_FOCUS),
                RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(stopPlayFromTimerReceiver, IntentFilter(ACTION_STOP))
            registerReceiver(restoreUIReceiver, IntentFilter(ACTION_GET_STATE))
            registerReceiver(setStopTimerReceiver, IntentFilter(ACTION_SET_TIMER))
            registerReceiver(pLayPauseReceiver, IntentFilter(ACTION_PLAY_PAUSE))
            registerReceiver(requestFocusReceiver, IntentFilter(ACTION_REQUEST_AUDIO_FOCUS))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupResources()
    }

    private fun cleanupResources() {
        audioManager.abandonAudioFocus(onAudioFocusChangeListener)
        player?.let {
            it.stop()
            it.removeListener(exoplayerEventListener)
            it.release()
        }
        isPlaying = false
        stateChange = ""
        broadcastState(stateChange)
        mediaSession.isActive = false
        mediaSession.release()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancelAll()
        unregisterAllReceivers()
    }

    private fun unregisterAllReceivers() {
        unregisterReceiver(stopPlayFromTimerReceiver)
        unregisterReceiver(setStopTimerReceiver)
        unregisterReceiver(restoreUIReceiver)
        unregisterReceiver(pLayPauseReceiver)
        unregisterReceiver(requestFocusReceiver)
    }

    private fun play(link: String) {
        stateChange = StreamStates.PREPARING
        preparePlayer(link.toUri())
        requestFocus()
        player?.play()
        updateMediaStyleNotification()
    }

    private fun preparePlayer(uri: Uri) {
        player?.release()
        val mediaItem = MediaItem.fromUri(uri)
        player = ExoPlayer.Builder(this).build().apply {
            setMediaItem(mediaItem)
            prepare()
            addListener(exoplayerEventListener)
        }
    }

    private fun prepareShowAd() {
        abandonFocus()
        player?.let {
            it.stop()
            it.release()
        }
        isPlaying = false
        stateChange = StreamStates.PREPARING
        broadcastState(stateChange)
        updateMediaStyleNotification()
    }

    private fun broadcastState(state: String) {
        eventIntent.putExtra(EXTRA_STATE, state)
        sendBroadcast(eventIntent)
    }

    private fun requestFocus() {
        audioManager.requestAudioFocus(
            onAudioFocusChangeListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )
    }

    private fun abandonFocus() {
        audioManager.abandonAudioFocus(onAudioFocusChangeListener)
    }

    private fun sendMetadataBroadcast(metadata: MediaMetadata) {
        if (metadata.title != null && isPlaying) {
            val currentTitle = metadata.title.toString()
            metadataIntent.putExtra(EXTRA_TITLE, currentTitle)
            sendBroadcast(metadataIntent)
        }
    }

    private fun pausePlayer(source: String?) {
        player?.pause()
        if (source == null) abandonFocus()
        isPlaying = false
        stateChange = StreamStates.IDLE
        broadcastState(stateChange)
        updateMediaStyleNotification()
    }

    private fun resumePlayer() {
        requestFocus()
        player?.play()
        isPlaying = true
        stateChange = StreamStates.PLAYING
        broadcastState(stateChange)
        updateMediaStyleNotification()
        mediaMetadataOR = player?.mediaMetadata
        mediaMetadataOR?.let { sendMetadataBroadcast(it) }
    }

    inner class PLayPauseReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val source = intent.getStringExtra(EXTRA_SOURCE)
            if (isPlaying) {
                pausePlayer(source)
            } else {
                resumePlayer()
            }
        }
    }

    inner class StopPlayFromTimerReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Toast.makeText(context, getString(R.string.stopped), Toast.LENGTH_SHORT).show()
            stopSelf()
        }
    }

    inner class SetStopTimerReceiver : BroadcastReceiver() {
        private val stopPlayFromTimerIntent = Intent(ACTION_STOP).setPackage(packageName)

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

    inner class RequestFocusReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            player?.play()
            requestFocus()
        }
    }

    inner class RestoreUIReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            player?.let {
                eventIntent.putExtra(EXTRA_STATE, stateChange)
                sendBroadcast(eventIntent)
                mediaMetadataOR = it.mediaMetadata
                mediaMetadataOR?.let { metadata -> sendMetadataBroadcast(metadata) }
            }
        }
    }

    inner class EventListener : Player.Listener {
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            mediaMetadataOR = mediaMetadata
            sendMetadataBroadcast(mediaMetadata)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                requestFocus()
                stateChange = StreamStates.PLAYING
                this@StreamService.isPlaying = true
                broadcastState(stateChange)
                updateMediaStyleNotification()
            }
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
                Player.STATE_BUFFERING -> handleBufferingState()
                Player.STATE_IDLE -> handleIdleState()
                Player.STATE_READY -> handleReadyState()
                Player.STATE_ENDED -> handleEndedState()
            }
        }

        private fun handleBufferingState() {
            abandonFocus()
            stateChange = StreamStates.BUFFERING
            broadcastState(stateChange)
            updateMediaStyleNotification()
        }

        private fun handleIdleState() {
            abandonFocus()
            stateChange = StreamStates.IDLE
            isPlaying = false
            broadcastState(stateChange)
            updateMediaStyleNotification()
        }

        private fun handleReadyState() {
            requestFocus()
            stateChange = StreamStates.PLAYING
            isPlaying = true
            broadcastState(stateChange)
            updateMediaStyleNotification()
        }

        private fun handleEndedState() {
            abandonFocus()
            stateChange = StreamStates.ENDED
            isPlaying = false
            broadcastState(stateChange)
            updateMediaStyleNotification()
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
        // Broadcast Actions
        const val ACTION_START = "SmoothService:Start"
        const val ACTION_STOP = "SmoothService:Stop"
        const val ACTION_SHOW_AD = "SmoothService:ShowAd"
        const val ACTION_EVENT_CHANGE = "SmoothService:EventChangeListener"
        const val ACTION_METADATA_CHANGE = "SmoothService:MetadataChangeListener"
        const val ACTION_GET_STATE = "SmoothService:GetState"
        const val ACTION_SET_TIMER = "SmoothService:SetTimer"
        private const val ACTION_PLAY_PAUSE = "SmoothService:PlayPause"
        private const val ACTION_REQUEST_AUDIO_FOCUS = "SmoothService:RequestAudioFocus"
        private const val NOTIFICATION_ID = 1

        // Broadcast Extras
        const val EXTRA_STATE = "state"
        const val EXTRA_TIME_IN_MILLIS = "timeInMillis"
        const val EXTRA_LOGO = "logo"
        const val EXTRA_STATION_NAME = "stationName"
        const val EXTRA_LINK = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_SOURCE = "source"

        // Notification Info
        private const val CHANNEL_ID = "media_playback_channel"
        private const val TITLE_PLAY = "Play"
        private const val TITLE_PAUSE = "Pause"
        private const val TITLE_STOP = "Stop"
    }
}
