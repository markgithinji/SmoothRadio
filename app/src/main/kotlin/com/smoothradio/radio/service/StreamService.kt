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
import android.content.pm.ServiceInfo
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
import androidx.media3.common.MimeTypes
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
import com.smoothradio.radio.core.util.LocalAudioProxy
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
    lateinit var localAudioProxy: LocalAudioProxy

    @Inject
    @JvmField
    var castPlayer: CastPlayer? = null

    private lateinit var wrappedPlayer: Player
    private lateinit var exoplayerEventListener: EventListener
    private var mediaSession: MediaSession? = null
    private var notificationCallback: MediaNotification.Provider.Callback? = null

    private var equalizer: Equalizer? = null
    private var audioSessionId: Int = 0
    private var maxPositionReached: Long = 0L
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var stopPlayFromTimerReceiver: StopPlayFromTimerReceiver
    private lateinit var setStopTimerReceiver: SetStopTimerReceiver

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Only stop the service if we are not playing anything.
        // This allows the radio to keep playing even if the app is swiped away from Recents.
        if (!wrappedPlayer.isPlaying && !wrappedPlayer.playWhenReady) {
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        setupWrappedPlayer()
        exoplayerEventListener = EventListener()
        wrappedPlayer.addListener(exoplayerEventListener)
        setupMediaSession()
        setupNotificationChannel()
        registerTimerReceivers()
        setMediaNotificationProvider(CustomNotificationProvider())
        startProgressUpdate()
    }

    private fun startProgressUpdate() {
        serviceScope.launch {
            while (true) {
                try {
                    val pos = wrappedPlayer.currentPosition
                    val dur = wrappedPlayer.duration
                    
                    if (pos > maxPositionReached) {
                        maxPositionReached = pos
                    }

                    // Log progress every 5 seconds to avoid spam but keep track
                    if (System.currentTimeMillis() % 5000 < 1000) {
                        Log.d("SmoothSeek", "Progress Update: Pos=$pos, MaxPos=$maxPositionReached, Dur=$dur")
                    }

                    if (pos > maxPositionReached) {
                        maxPositionReached = pos
                    }

                    // Total capacity of the buffer in milliseconds
                    val bufferCapacityMs = LocalAudioProxy.TOTAL_CAPACITY_BYTES / LocalAudioProxy.BYTES_PER_MS

                    // Show the full 25-minute window as requested.
                    // If they listen longer, the bar expands.
                    val displayDur = bufferCapacityMs.coerceAtLeast(maxPositionReached)
                    val loadedPos = localAudioProxy.getLoadedDurationMs()
                    
                    stateRepository.updatePosition(if (pos < 0) 0 else pos)
                    stateRepository.updateDuration(displayDur)
                    stateRepository.updateMinPosition(localAudioProxy.getDroppedDurationMs())
                    stateRepository.updateLoadedPosition(loadedPos)
                } catch (e: Exception) {
                    Log.e("SmoothSeek", "Error in progress update", e)
                }
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    private fun setupWrappedPlayer() {
        val basePlayer = castPlayer ?: player
        wrappedPlayer = object : ForwardingPlayer(basePlayer) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(COMMAND_SEEK_BACK)
                    .add(COMMAND_SEEK_FORWARD)
                    .add(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                    .add(COMMAND_SEEK_TO_DEFAULT_POSITION)
                    .add(COMMAND_PLAY_PAUSE)
                    .remove(COMMAND_SEEK_TO_NEXT)
                    .remove(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .remove(COMMAND_SEEK_TO_PREVIOUS)
                    .remove(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    COMMAND_SEEK_BACK,
                    COMMAND_SEEK_FORWARD,
                    COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                    COMMAND_SEEK_TO_DEFAULT_POSITION,
                    COMMAND_PLAY_PAUSE -> true

                    COMMAND_SEEK_TO_NEXT,
                    COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    COMMAND_SEEK_TO_PREVIOUS,
                    COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> false

                    else -> super.isCommandAvailable(command)
                }
            }

            override fun isCurrentMediaItemLive(): Boolean = false

            override fun isCurrentMediaItemSeekable(): Boolean = true

            override fun getDuration(): Long {
                val baseDur = super.getDuration()
                // If the stream doesn't have a duration (live), use the furthest point we've reached
                return if (baseDur > 0) baseDur else maxPositionReached
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
            .setOngoing(true)
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
        val notification = createMediaStyleNotification()
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)

        notificationCallback?.onNotificationChanged(
            MediaNotification(NOTIFICATION_ID, notification)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val action = it.action
            if (action == ACTION_START || action == ACTION_SHOW_AD) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIFICATION_ID,
                            createMediaStyleNotification(),
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, createMediaStyleNotification())
                    }
                } catch (e: Exception) {
                    Log.e("StreamService", "Failed to start foreground", e)
                }
            }
            handleIntent(it)
        }
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

        // Only update notification here if it's a metadata-changing action
        if (action == ACTION_START || action == ACTION_SHOW_AD || action == ACTION_STOP) {
            updateNotificationInternal()
        }

        when (action) {
            ACTION_START -> {
                Log.d("StreamService", " ACTION_START → ${currentStationName}")
                isPreparingForAd = false
                maxPositionReached = 0L // Reset for new station
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
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            ACTION_PLAY -> {
                Log.d("SmoothSeek", "ACTION_PLAY received")
                wrappedPlayer.play()
            }
            ACTION_PAUSE -> {
                Log.d("SmoothSeek", "ACTION_PAUSE received")
                wrappedPlayer.pause()
            }
            ACTION_SEEK_BACK -> {
                val current = wrappedPlayer.currentPosition
                val target = (current - 10000).coerceAtLeast(0)
                Log.d("SmoothSeek", "ACTION_SEEK_BACK: current=$current -> target=$target")
                wrappedPlayer.seekTo(target)
            }
            ACTION_SEEK_FORWARD -> {
                val current = wrappedPlayer.currentPosition
                val loadedDur = localAudioProxy.getLoadedDurationMs()
                val target = (current + 10000).coerceAtMost(loadedDur)
                Log.d("SmoothSeek", "ACTION_SEEK_FORWARD: current=$current -> target=$target")
                wrappedPlayer.seekTo(target)
            }
            ACTION_SEEK_TO -> {
                val position = intent.getLongExtra(EXTRA_POSITION, 0L)
                val loadedDur = localAudioProxy.getLoadedDurationMs()
                val target = position.coerceAtMost(loadedDur)
                
                Log.d("SmoothSeek", "ACTION_SEEK_TO: requested=$position, loaded=$loadedDur, target=$target")
                wrappedPlayer.seekTo(target)
                // Immediately update repository to prevent UI flicker
                stateRepository.updatePosition(target)
            }
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
        
        // Reset seek history for new play
        maxPositionReached = 0L
        
        val uriString = uri.toString()
        val isHls = uriString.contains(".m3u8") || uriString.contains("playlist")
        
        // Use the LocalProxy for EVERYTHING
        localAudioProxy.start(uriString)
        val proxyUri = localAudioProxy.proxyUrl.toUri()
        
        val cacheKey = currentStationName ?: uriString
        
        // Smarter MimeType detection: 
        // HLS audio segments are typically AAC (ADTS). Progressive is usually MP3.
        val mimeType = when {
            isHls -> MimeTypes.AUDIO_AAC 
            uriString.contains(".aac") -> MimeTypes.AUDIO_AAC
            else -> MimeTypes.AUDIO_MPEG
        }

        Log.d("SmoothSeek", "Preparing player via Proxy. Mode: ${if(isHls) "HLS-to-AAC" else "Progressive"}, Mime: $mimeType")
        
        val mediaItem = MediaItem.Builder()
            .setUri(proxyUri)
            .setMimeType(mimeType)
            .setCustomCacheKey(cacheKey)
            .build()
        wrappedPlayer.setMediaItem(mediaItem)
        wrappedPlayer.prepare()
    }

    private fun prepareShowAd() {
        wrappedPlayer.stop()
        isPlaying = false
        updateNotificationInternal()
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
        localAudioProxy.stop()
        equalizer?.release()
        equalizer = null
        wrappedPlayer.removeListener(exoplayerEventListener)
        wrappedPlayer.release()
        mediaSession?.release()
        mediaSession = null
        isPlaying = false
        stateChange = StreamStates.IDLE
        stateRepository.updateState(StreamStates.IDLE) // Reset repo state on destroy
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
            Log.d("SmoothSeek", "onIsPlayingChanged: $isPlaying, State=${wrappedPlayer.playbackState}")
            updateNotificationInternal()
            if (isPreparingForAd) return
            
            // Avoid setting IDLE state during seek/buffer transitions
            if (!isPlaying && (wrappedPlayer.playbackState == Player.STATE_BUFFERING || wrappedPlayer.playbackState == Player.STATE_IDLE)) {
                return
            }

            val newState = if (isPlaying) StreamStates.PLAYING
            else if (wrappedPlayer.playbackState == Player.STATE_READY) StreamStates.IDLE
            else return
            setState(newState)
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("SmoothSeek", "Player Error: Code=${error.errorCode}, Message=${error.message}", error)
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                // If we seek too far back and lose the window, jump to live
                wrappedPlayer.seekToDefaultPosition()
                wrappedPlayer.prepare()
                return
            }
            val message = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> getString(R.string.toast_station_unreachable)

                else -> getString(R.string.toast_unexpected_error)
            }
            Toast.makeText(this@StreamService, message, Toast.LENGTH_SHORT).show()
        }

        override fun onPlaybackStateChanged(state: Int) {
            val stateName = when(state) {
                Player.STATE_IDLE -> "IDLE"
                Player.STATE_BUFFERING -> "BUFFERING"
                Player.STATE_READY -> "READY"
                Player.STATE_ENDED -> "ENDED"
                else -> "UNKNOWN"
            }
            Log.d("SmoothSeek", "Playback State Changed: $stateName, Pos=${wrappedPlayer.currentPosition}")

            if (isPreparingForAd) return
            val newState = when (state) {
                Player.STATE_BUFFERING -> StreamStates.BUFFERING
                Player.STATE_IDLE -> StreamStates.IDLE
                Player.STATE_READY -> if (wrappedPlayer.isPlaying) StreamStates.PLAYING else StreamStates.IDLE
                Player.STATE_ENDED -> StreamStates.ENDED
                else -> return
            }
            setState(newState)
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            val reasonName = when(reason) {
                Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> "AUTO"
                Player.DISCONTINUITY_REASON_SEEK -> "SEEK"
                Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT -> "SEEK_ADJUST"
                Player.DISCONTINUITY_REASON_SKIP -> "SKIP"
                Player.DISCONTINUITY_REASON_REMOVE -> "REMOVE"
                Player.DISCONTINUITY_REASON_INTERNAL -> "INTERNAL"
                else -> "UNKNOWN"
            }
            Log.d("SmoothSeek", "Position Discontinuity: Reason=$reasonName, Old=${oldPosition.positionMs}, New=${newPosition.positionMs}")
        }
    }

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
        const val ACTION_SET_EQ_BAND = "SmoothService:SetEqBand"
        private const val ACTION_STOP_FROM_TIMER = "SmoothService:StopFromTimer"
        private const val NOTIFICATION_ID = 1
        const val EXTRA_TIME_IN_MILLIS = "timeInMillis"
        const val EXTRA_LOGO = "logo"
        const val EXTRA_STATION_NAME = "stationName"
        const val EXTRA_LINK = "url"
        const val EXTRA_POSITION = "position"
        const val EXTRA_BAND = "band"
        const val EXTRA_LEVEL = "level"
        private const val CHANNEL_ID = "media_playback_channel"
    }
}
