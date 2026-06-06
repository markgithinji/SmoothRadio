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
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.smoothradio.radio.MainActivity
import com.smoothradio.radio.R
import com.smoothradio.radio.core.domain.model.StreamStates
import com.smoothradio.radio.core.domain.repository.EqualizerRepository
import com.smoothradio.radio.core.domain.repository.PlaybackStateRepository
import com.smoothradio.radio.core.util.PlaybackConstants
import com.smoothradio.radio.service.util.BitrateEstimator
import com.smoothradio.radio.service.util.BufferEvictedException
import com.smoothradio.radio.service.util.EmptyStreamException
import com.smoothradio.radio.service.util.LocalAudioProxy
import com.smoothradio.radio.service.util.MetadataUtils
import com.smoothradio.radio.service.util.PlaybackProgressCalculator
import com.smoothradio.radio.service.util.ProxyCacheException
import com.smoothradio.radio.service.util.ServiceCommand
import com.smoothradio.radio.service.util.ServiceCommandMapper
import com.smoothradio.radio.service.util.StationUnreachableException
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

/**
 * A background service that manages audio streaming using ExoPlayer and Media3 MediaSession.
 *
 */
@AndroidEntryPoint
class StreamService : MediaSessionService() {

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
    lateinit var bitrateEstimator: BitrateEstimator

    @Inject
    lateinit var progressCalculator: PlaybackProgressCalculator

    @Inject
    lateinit var commandMapper: ServiceCommandMapper

    @Inject
    @JvmField
    var castPlayer: CastPlayer? = null

    @Inject
    @JvmField
    var castContext: CastContext? = null

    private lateinit var wrappedPlayer: Player
    private lateinit var exoplayerEventListener: EventListener
    private var mediaSession: MediaSession? = null
    private var notificationCallback: MediaNotification.Provider.Callback? = null

    private var equalizer: Equalizer? = null
    private var audioSessionId: Int = 0
    private var maxPositionReached: Long = 0L
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressUpdateJob: Job? = null

    private lateinit var stopPlayFromTimerReceiver: StopPlayFromTimerReceiver
    private lateinit var setStopTimerReceiver: SetStopTimerReceiver

    private val castSessionListener = CastSessionListener()

    private var jumpToLiveOnReady = false
    private var estimatedBytesPerMs: Double = PlaybackConstants.INITIAL_BITRATE_ESTIMATION
    private var sessionStartTime: Long = 0L
    private var preparationStartTime: Long = 0L
    private var playbackBaseTimeMs: Long = 0L
    private var activeStreamUrl: String? = null

    private fun updateBitrateEstimation() {
        if (sessionStartTime == 0L) return
        
        val newEstimation = bitrateEstimator.calculate(
            totalBytesWritten = localAudioProxy.totalBytesWritten,
            elapsedTimeMs = System.currentTimeMillis() - sessionStartTime,
            manifestBitrateKbps = localAudioProxy.detectedBitrateKbps,
            currentEstimation = estimatedBytesPerMs
        )
        
        if (abs(newEstimation - estimatedBytesPerMs) > 1.0) {
            Log.d("SmoothSeek", "Bitrate estimation updated: $newEstimation")
        }
        estimatedBytesPerMs = newEstimation
    }

    private fun getDroppedDurationMs(): Long = (localAudioProxy.totalBytesDropped / estimatedBytesPerMs).toLong()
    private fun getLoadedDurationMs(): Long = (localAudioProxy.totalBytesWritten / estimatedBytesPerMs).toLong()

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
        syncProgressTracker()
        
        // Start listening for Cast sessions
        castContext?.sessionManager?.addSessionManagerListener(
            castSessionListener, 
            CastSession::class.java
        )
    }

    private fun syncProgressTracker() {
        val state = wrappedPlayer.playbackState
        val isPlaying = wrappedPlayer.isPlaying
        
        // We should track if we are actively playing, buffering, or preparing (including shadow loading for ads)
        val shouldTrack = isPreparingForAd || jumpToLiveOnReady || 
                         state == Player.STATE_BUFFERING || 
                         (state == Player.STATE_READY && isPlaying)

        if (shouldTrack) {
            if (progressUpdateJob == null || progressUpdateJob?.isActive == false) {
                startProgressUpdate()
            }
        } else {
            progressUpdateJob?.cancel()
            progressUpdateJob = null
            // Perform one final sync to ensure UI reflects the final stopped/paused state
            serviceScope.launch { performProgressUpdate() }
        }
    }

    private fun startProgressUpdate() {
        progressUpdateJob?.cancel()
        progressUpdateJob = serviceScope.launch {
            // Ticker Flow: Adaptive delay based on player state
            flow {
                while (true) {
                    emit(Unit)
                    val currentState = wrappedPlayer.playbackState
                    val isBusy = jumpToLiveOnReady || currentState == Player.STATE_BUFFERING || (currentState == Player.STATE_READY && wrappedPlayer.isPlaying)
                    val delayMs = if (isBusy) PlaybackConstants.BUSY_PROGRESS_UPDATE_DELAY_MS else PlaybackConstants.IDLE_PROGRESS_UPDATE_DELAY_MS
                    delay(delayMs)
                }
            }.collect {
                performProgressUpdate()
            }
        }
    }

    private fun performProgressUpdate() {
        try {
            val pos = wrappedPlayer.currentPosition

            if (pos > maxPositionReached) {
                maxPositionReached = pos
            }

            updateBitrateEstimation()

            val urlString = activeStreamUrl ?: ""
            val isHls = urlString.contains(".m3u8") || urlString.contains("playlist")
            val isBuffering = jumpToLiveOnReady || stateChange == StreamStates.BUFFERING || stateChange == StreamStates.PREPARING

            val snapshot = progressCalculator.calculate(
                currentPosition = wrappedPlayer.currentPosition,
                totalBytesWritten = localAudioProxy.totalBytesWritten,
                totalBytesDropped = localAudioProxy.totalBytesDropped,
                totalBytesReceived = localAudioProxy.totalBytesReceived,
                estimatedBytesPerMs = estimatedBytesPerMs,
                isHls = isHls,
                totalCapacityBytes = LocalAudioProxy.TOTAL_CAPACITY_BYTES,
                isBuffering = isBuffering
            )

            // Update metadata from proxy if available (handles Time Machine seeking)
            val byteOffset = (snapshot.position * estimatedBytesPerMs).toLong()
            val proxyMetadata = localAudioProxy.getMetadataForOffset(byteOffset)

            if (proxyMetadata != null) {
                val cleaned = MetadataUtils.extractSongTitle(proxyMetadata)
                if (cleaned.isNotEmpty() && cleaned != currentSongTitle) {
                    currentSongTitle = cleaned
                    stateRepository.updateMetadata(cleaned)
                    updateNotificationInternal()
                }
            }

            stateRepository.updatePosition(snapshot.position)
            stateRepository.updateDuration(snapshot.duration)
            stateRepository.updateMinPosition(snapshot.minPosition)
            stateRepository.updateLoadedPosition(snapshot.loadedPosition)
            stateRepository.updateLoadingProgress(snapshot.loadingProgress)
        } catch (e: Exception) {
            // Silently handle transient errors during state transitions
        }
    }


    private fun setupWrappedPlayer() {
        // ALWAYS start with local player. We will swap to castPlayer dynamically.
        val basePlayer = player
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

                override fun isCurrentMediaItemLive(): Boolean = super.isCurrentMediaItemLive()

                override fun isCurrentMediaItemSeekable(): Boolean = true

                override fun getCurrentPosition(): Long {
                    // Calculate absolute position based on our custom seek base
                    return playbackBaseTimeMs + super.getCurrentPosition()
                }

            override fun getDuration(): Long {
                val baseDur = super.getDuration()
                // Use a large virtual duration (24 hours) for live streams to allow infinite seeking
                return if (baseDur > 0) baseDur else 24 * 60 * 60 * 1000L
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
            .setCallback(CustomMediaSessionCallback())
            .build()
    }

    private inner class CustomMediaSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(ServiceCommand.COMMAND_SET_EQ_BAND, Bundle.EMPTY))
                .add(SessionCommand(ServiceCommand.COMMAND_SET_SLEEP_TIMER, Bundle.EMPTY))
                .build()
            
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ServiceCommand.COMMAND_SET_EQ_BAND -> {
                    val band = args.getInt(ServiceCommand.EXTRA_BAND, -1)
                    val level = args.getShort(ServiceCommand.EXTRA_LEVEL, 0)
                    if (band != -1) setEqualizerBand(band, level)
                }
                ServiceCommand.COMMAND_SET_SLEEP_TIMER -> {
                    val minutes = args.getInt("minutes", 0)
                    // We can reuse the existing broadcast logic or implement directly
                    val timeInMillis = System.currentTimeMillis() + (minutes * 60 * 1000L)
                    val intent = Intent(ServiceCommand.ACTION_SET_TIMER).apply {
                        setPackage(packageName)
                        putExtra(ServiceCommand.EXTRA_TIME_IN_MILLIS, timeInMillis)
                    }
                    sendBroadcast(intent)
                }
            }
            return com.google.common.util.concurrent.Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            // Intercept media items added via controller.setMediaUri() etc.
            val updatedItems = mediaItems.map { item ->
                val uri = item.localConfiguration?.uri.toString()
                // If it's a real URL, wrap it in our proxy scheme
                if (uri.startsWith("http")) {
                    val name = item.mediaMetadata.title?.toString() ?: ""
                    // We need to handle this like ACTION_START
                    // Note: This is simplified, real logic would need to store metadata
                    item.buildUpon()
                        .setUri("proxy://smoothradio/stream?byteOffset=0".toUri())
                        .build()
                } else item
            }.toMutableList()
            return com.google.common.util.concurrent.Futures.immediateFuture(updatedItems)
        }
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
            IntentFilter(ServiceCommand.ACTION_STOP_FROM_TIMER),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ContextCompat.registerReceiver(
            this,
            setStopTimerReceiver,
            IntentFilter(ServiceCommand.ACTION_SET_TIMER),
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
            action = if (wrappedPlayer.isPlaying) ServiceCommand.ACTION_PAUSE else ServiceCommand.ACTION_PLAY
        }

        val playPausePendingIntent = PendingIntent.getService(
            this, 2, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, StreamService::class.java).apply {
            action = ServiceCommand.ACTION_STOP
        }

        val stopPendingIntent = PendingIntent.getService(
            this, 3, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when {
            wrappedPlayer.isPlaying && currentSongTitle.isNotEmpty() -> currentSongTitle
            wrappedPlayer.isPlaying -> getString(R.string.player_playing)
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
            if (action == ServiceCommand.ACTION_START || action == ServiceCommand.ACTION_SHOW_AD) {
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
        val command = commandMapper.map(intent)

        // Metadata and notification updates
        when (command) {
            is ServiceCommand.Start -> {
                currentStationName = command.name
                currentStationLogo = command.logo
                stateRepository.updateStationName(command.name ?: "")
                updateNotificationInternal()
            }
            is ServiceCommand.ShowAd -> {
                currentStationName = command.name
                currentStationLogo = command.logo
                stateRepository.updateStationName(command.name ?: "")
                updateNotificationInternal()
            }
            is ServiceCommand.Stop -> {
                updateNotificationInternal()
            }
            else -> {}
        }

        when (command) {
            is ServiceCommand.Start -> {
                Log.d("StreamService", " ACTION_START → ${currentStationName}")
                isPreparingForAd = false
                maxPositionReached = 0L // Reset for new station
                currentSongTitle = "" // Clear stale metadata
                playbackBaseTimeMs = 0L
                activeStreamUrl = command.link
                
                // RESET UI STATE
                stateRepository.updatePosition(0L)
                stateRepository.updateDuration(0L)
                stateRepository.updateMinPosition(0L)
                stateRepository.updateLoadedPosition(0L)
                stateRepository.updateMetadata("")
                stateRepository.updateLoadingProgress(0f)

                setState(StreamStates.PREPARING)
                play(command.link)
            }

            is ServiceCommand.ShowAd -> {
                Log.d("StreamService", " ACTION_SHOW_AD → ${currentStationName}")
                isPreparingForAd = true
                maxPositionReached = 0L // Reset history immediately
                sessionStartTime = System.currentTimeMillis() // Start calibration early
                currentSongTitle = "" // Clear stale metadata
                playbackBaseTimeMs = 0L
                activeStreamUrl = command.link
                
                // SHADOW LOADING: Start downloading the stream while the ad is showing
                if (command.link.isNotEmpty()) {
                    Log.d("StreamService", "Shadow loading started for: ${command.link}")
                    localAudioProxy.start(command.link)
                }
                
                // RESET UI STATE IMMEDIATELY
                stateRepository.updatePosition(0L)
                stateRepository.updateDuration(0L)
                stateRepository.updateMinPosition(0L)
                stateRepository.updateLoadedPosition(0L)
                stateRepository.updateMetadata("")
                stateRepository.updateLoadingProgress(0f)

                setState(StreamStates.PREPARING)
                prepareShowAd(command.link)
            }

            is ServiceCommand.Stop -> {
                Log.d("StreamService", " ACTION_STOP")
                isPreparingForAd = false
                wrappedPlayer.pause()
                wrappedPlayer.stop()
                wrappedPlayer.clearMediaItems()
                currentSongTitle = ""
                
                // RESET UI STATE
                stateRepository.updatePosition(0L)
                stateRepository.updateDuration(0L)
                stateRepository.updateMinPosition(0L)
                stateRepository.updateLoadedPosition(0L)

                setState(StreamStates.IDLE)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            is ServiceCommand.Play -> {
                Log.d("SmoothSeek", "ACTION_PLAY received. Catching up to live.")
                val loadedDur = getLoadedDurationMs()
                val urlString = activeStreamUrl ?: ""
                val isHls = urlString.contains(".m3u8") || urlString.contains("playlist")
                
                // Use a larger safety margin for HLS vs Progressive
                val safetyBuffer = if (isHls) PlaybackConstants.HLS_SAFETY_BUFFER_MS else PlaybackConstants.PROGRESSIVE_SAFETY_BUFFER_MS
                seekToAbsolute((loadedDur - safetyBuffer).coerceAtLeast(0))
            }
            is ServiceCommand.Pause -> {
                Log.d("SmoothSeek", "ACTION_PAUSE received")
                wrappedPlayer.pause()
            }
            is ServiceCommand.SeekBack -> {
                val current = wrappedPlayer.currentPosition
                val droppedDur = getDroppedDurationMs()
                val target = (current - PlaybackConstants.SEEK_INCREMENT_MS).coerceAtLeast(droppedDur)
                Log.d("SmoothSeek", "ACTION_SEEK_BACK: current=$current -> target=$target")
                seekToAbsolute(target)
            }
            is ServiceCommand.SeekForward -> {
                val current = wrappedPlayer.currentPosition
                val loadedDur = getLoadedDurationMs()
                val urlString = activeStreamUrl ?: ""
                val isHls = urlString.contains(".m3u8") || urlString.contains("playlist")
                
                // Don't seek past the safety buffer
                val safetyBuffer = if (isHls) PlaybackConstants.HLS_SAFETY_BUFFER_MS else PlaybackConstants.PROGRESSIVE_SAFETY_BUFFER_MS
                val target = (current + PlaybackConstants.SEEK_INCREMENT_MS).coerceAtMost(loadedDur - safetyBuffer)
                Log.d("SmoothSeek", "ACTION_SEEK_FORWARD: current=$current -> target=$target")
                seekToAbsolute(target)
            }
            is ServiceCommand.SeekTo -> {
                val position = command.position
                val loadedDur = getLoadedDurationMs()
                val droppedDur = getDroppedDurationMs()
                val urlString = activeStreamUrl ?: ""
                val isHls = urlString.contains(".m3u8") || urlString.contains("playlist")
                
                // Physical coercion: target must be between oldest data and the live safety buffer
                val safetyBuffer = if (isHls) PlaybackConstants.HLS_SAFETY_BUFFER_MS else PlaybackConstants.PROGRESSIVE_SAFETY_BUFFER_MS
                val target = position.coerceIn(droppedDur, (loadedDur - safetyBuffer).coerceAtLeast(droppedDur))
                
                Log.d("SmoothSeek", "ACTION_SEEK_TO: requested=$position, available=$droppedDur..$loadedDur, target=$target")
                seekToAbsolute(target)
            }
            is ServiceCommand.SetEqBand -> {
                if (command.band != -1) setEqualizerBand(command.band, command.level)
            }
            ServiceCommand.None -> {}
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
        if (link.isEmpty()) {
            Log.e("StreamService", "Cannot play: link is empty")
            setState(StreamStates.IDLE)
            return
        }

        // INSTANT PLAYBACK: If we are already warming up this link, just hit play
        if (activeStreamUrl == link && (wrappedPlayer.playbackState == Player.STATE_READY || wrappedPlayer.playbackState == Player.STATE_BUFFERING)) {
            Log.d("SmoothSeek", "Player already warmed up for $link. Activating now.")
            isPreparingForAd = false
            wrappedPlayer.playWhenReady = true
            performInitialJump()
            return
        }

        isPreparingForAd = false
        activeStreamUrl = link
        sessionStartTime = System.currentTimeMillis()
        preparationStartTime = sessionStartTime
        estimatedBytesPerMs = 16.0 // Reset to default for fresh calibration
        Log.d("SmoothSeek", "play() called at $preparationStartTime for $link")
        
        val isHls = link.contains(".m3u8") || link.contains("playlist")
        if (isHls) {
            // For HLS, we need to wait for at least one segment before jumping to live
            // We'll let preparePlayer start at 0, then jump in onPlaybackStateChanged
            jumpToLiveOnReady = true
        } else {
            // For Progressive, we start at the live edge (byte 0) naturally
            jumpToLiveOnReady = false
        }
        
        preparePlayer(link.toUri())
    }

    private fun preparePlayer(uri: Uri) {
        wrappedPlayer.stop()
        
        // Reset seek history for new play
        maxPositionReached = 0L
        jumpToLiveOnReady = true
        playbackBaseTimeMs = 0L
        preparationStartTime = System.currentTimeMillis()
        Log.d("SmoothSeek", "preparePlayer() started at $preparationStartTime")
        
        val uriString = uri.toString()
        
        val isHls = uriString.contains(".m3u8") || uriString.contains("playlist")

        // Only start if not already shadow loading this specific URL
        if (!localAudioProxy.isStartedFor(uriString)) {
            localAudioProxy.start(uriString)
        }

        // Use a custom scheme with byteOffset to ensure our ProxyDataSource is used
        val proxyUri = "proxy://smoothradio/stream?byteOffset=0".toUri()
        
        val cacheKey = currentStationName ?: uriString
        
        // MimeType detection:
        val (mimeType, streamType) = when {
            isHls -> MimeTypes.AUDIO_AAC to "HLS (AAC)"
            uriString.contains(".aac") -> MimeTypes.AUDIO_AAC to "AAC"
            uriString.contains(".mp3") -> MimeTypes.AUDIO_MPEG to "MP3"
            else -> MimeTypes.AUDIO_MPEG to "Progressive (Default: MP3)"
        }

        Log.d("SmoothSeek", "**************************************************")
        Log.d("SmoothSeek", ">>> STREAM TYPE DETECTED: $streamType")
        Log.d("SmoothSeek", ">>> URL: $uriString")
        Log.d("SmoothSeek", "**************************************************")
        
        val mediaItem = MediaItem.Builder()
            .setUri(proxyUri)
            .setMimeType(mimeType)
            .setCustomCacheKey(cacheKey)
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(PlaybackConstants.LIVE_OFFSET_TARGET_MS)
                    .build()
            )
            .build()
        wrappedPlayer.setMediaItem(mediaItem)
        wrappedPlayer.prepare()
    }

    private fun seekToAbsolute(positionMs: Long) {
        val urlString = activeStreamUrl ?: return
        val isHls = urlString.contains(".m3u8") || urlString.contains("playlist")
        
        // Calculate byte offset from time using our calibrated bitrate
        val targetByte = (positionMs * estimatedBytesPerMs).toLong()
        
        // Construct new proxy URI with the specific byte offset
        // This bypasses ExoPlayer's time-to-byte mapping which fails for live streams.
        val proxyUri = "proxy://smoothradio/stream?byteOffset=$targetByte".toUri()
        
        val mimeType = if (isHls) MimeTypes.AUDIO_AAC else MimeTypes.AUDIO_MPEG
        val cacheKey = currentStationName ?: urlString

        val mediaItem = MediaItem.Builder()
            .setUri(proxyUri)
            .setMimeType(mimeType)
            .setCustomCacheKey(cacheKey)
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(PlaybackConstants.LIVE_OFFSET_TARGET_MS)
                    .build()
            )
            .build()
            
        Log.d("SmoothSeek", "seekToAbsolute: requestedMs=$positionMs -> targetByte=$targetByte")

        // Update the base time so getCurrentPosition() reports correctly
        playbackBaseTimeMs = positionMs

        // Restart playback at the new byte position
        wrappedPlayer.setMediaItem(mediaItem, true) // reset position to 0 in the "new" stream
        wrappedPlayer.prepare()
        wrappedPlayer.play()
        
        // Force immediate UI update to show the new seek position
        stateRepository.updatePosition(positionMs)
    }

    private fun prepareShowAd(link: String) {
        if (link.isEmpty()) {
            wrappedPlayer.stop()
            wrappedPlayer.clearMediaItems()
            updateNotificationInternal()
            return
        }

        Log.d("StreamService", "Warming up player for: $link")
        
        // Prepare exactly like play() but without playWhenReady
        maxPositionReached = 0L
        jumpToLiveOnReady = true
        playbackBaseTimeMs = 0L
        preparationStartTime = System.currentTimeMillis()
        
        val uriString = link
        val isHls = uriString.contains(".m3u8") || uriString.contains("playlist")
        val proxyUri = "proxy://smoothradio/stream?byteOffset=0".toUri()
        val cacheKey = currentStationName ?: uriString
        
        val (mimeType, _) = when {
            isHls -> MimeTypes.AUDIO_AAC to "HLS (AAC)"
            uriString.contains(".aac") -> MimeTypes.AUDIO_AAC to "AAC"
            uriString.contains(".mp3") -> MimeTypes.AUDIO_MPEG to "MP3"
            else -> MimeTypes.AUDIO_MPEG to "Progressive (Default: MP3)"
        }

        val mediaItem = MediaItem.Builder()
            .setUri(proxyUri)
            .setMimeType(mimeType)
            .setCustomCacheKey(cacheKey)
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(PlaybackConstants.LIVE_OFFSET_TARGET_MS)
                    .build()
            )
            .build()
            
        wrappedPlayer.playWhenReady = false // Stay silent during ad
        wrappedPlayer.setMediaItem(mediaItem)
        wrappedPlayer.prepare()
        
        updateNotificationInternal()
    }

    private fun performInitialJump() {
        if (!jumpToLiveOnReady) return
        
        val urlString = activeStreamUrl ?: ""
        val isHls = urlString.contains(".m3u8") || urlString.contains("playlist")
        
        if (isHls) {
            jumpToLiveOnReady = false
            val loadedDur = getLoadedDurationMs()
            // HLS SAFETY BUFFER: Jump back to ensure we don't hit the end of playlist immediately
            val target = (loadedDur - PlaybackConstants.HLS_SAFETY_BUFFER_MS).coerceAtLeast(0)
            Log.d("SmoothSeek", "HLS Initial Jump: loaded=$loadedDur -> target=$target")
            seekToAbsolute(target)
        } else if (wrappedPlayer.playWhenReady) {
            // For progressive, just mark as done once user actually hits play
            jumpToLiveOnReady = false
            Log.d("PlaybackLifecycle", "Station ready, starting play()")
            wrappedPlayer.play()
        }
    }

    private fun setupEqualizer(sessionId: Int) {
        if (sessionId == 0 || sessionId == audioSessionId) return
        audioSessionId = sessionId
        try {
            equalizer?.release()
            equalizer = Equalizer(0, sessionId).apply {
                enabled = true
                val bands = numberOfBands
                serviceScope.launch(Dispatchers.IO) {
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
        castContext?.sessionManager?.removeSessionManagerListener(
            castSessionListener,
            CastSession::class.java
        )
        localAudioProxy.stop()
        equalizer?.release()
        equalizer = null
        wrappedPlayer.removeListener(exoplayerEventListener)
        wrappedPlayer.release()
        mediaSession?.release()
        mediaSession = null
        stateChange = StreamStates.IDLE
        
        // Reset repository state on destroy
        stateRepository.updateState(StreamStates.IDLE)
        stateRepository.updatePosition(0L)
        stateRepository.updateDuration(0L)
        stateRepository.updateMinPosition(0L)
        stateRepository.updateLoadedPosition(0L)
        stateRepository.updateMetadata("")

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
        private val stopPlayFromTimerIntent = Intent(ServiceCommand.ACTION_STOP_FROM_TIMER).setPackage(packageName)
        override fun onReceive(context: Context, intent: Intent) {
            val timeInMillis = intent.getLongExtra(ServiceCommand.EXTRA_TIME_IN_MILLIS, 0)
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

    private fun updateUiState() {
        if (isPreparingForAd) return

        val playbackState = wrappedPlayer.playbackState
        val isPlayerPlaying = wrappedPlayer.isPlaying

        val newState = when {
            playbackState == Player.STATE_BUFFERING -> StreamStates.BUFFERING
            playbackState == Player.STATE_READY && isPlayerPlaying -> StreamStates.PLAYING
            playbackState == Player.STATE_READY && !isPlayerPlaying -> StreamStates.IDLE
            playbackState == Player.STATE_ENDED -> StreamStates.ENDED
            else -> StreamStates.IDLE
        }

        if (stateChange != newState) {
            setState(newState)
        }
    }

    private inner class CastSessionListener : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            switchToCastPlayer()
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            switchToCastPlayer()
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            switchToLocalPlayer()
        }

        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionStartFailed(session: CastSession, error: Int) {}
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {}
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
    }

    private fun switchToCastPlayer() {
        val cp = castPlayer ?: return
        
        // 1. Transfer current item to Cast
        if (player.currentMediaItem != null) {
            cp.setMediaItem(player.currentMediaItem!!, player.currentPosition)
            cp.prepare()
            cp.play()
        }
        
        // 2. Update MediaSession to point to the Cast player
        mediaSession?.setPlayer(cp)
    }

    private fun switchToLocalPlayer() {
        // 1. Transfer state back if possible
        if (castPlayer?.currentMediaItem != null) {
            player.setMediaItem(castPlayer!!.currentMediaItem!!, castPlayer!!.currentPosition)
            player.prepare()
            // We don't auto-play on phone after cast ends to avoid surprising the user
        }
        
        // 2. Point MediaSession back to our wrapped local player
        mediaSession?.setPlayer(wrappedPlayer)
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
            updateNotificationInternal()
            updateUiState()
            syncProgressTracker()
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("PlaybackLifecycle", "Player Error: Code=${error.errorCode}, Message=${error.message}", error)
            jumpToLiveOnReady = false // Stop high-frequency progress polling on error
            
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                wrappedPlayer.seekToDefaultPosition()
                wrappedPlayer.prepare()
                return
            }

            // Unwrap the cause to find our custom exceptions
            var rootCause: Throwable? = error
            while (rootCause != null && rootCause !is StationUnreachableException && 
                   rootCause !is EmptyStreamException && rootCause !is BufferEvictedException && 
                   rootCause !is ProxyCacheException) {
                rootCause = rootCause.cause
            }

            val message = when {
                rootCause is StationUnreachableException -> getString(R.string.toast_station_unreachable)
                rootCause is EmptyStreamException -> getString(R.string.toast_empty_stream)
                rootCause is BufferEvictedException -> getString(R.string.toast_buffer_evicted)
                rootCause is ProxyCacheException -> getString(R.string.toast_proxy_cache_error)
                
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ||
                error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> getString(R.string.toast_station_unreachable)

                else -> getString(R.string.toast_unexpected_error)
            }
            Toast.makeText(this@StreamService, message, Toast.LENGTH_SHORT).show()
        }

        override fun onPlaybackStateChanged(state: Int) {
            val now = System.currentTimeMillis()
            val duration = now - preparationStartTime
            
            if (state == Player.STATE_READY && jumpToLiveOnReady) {
                performInitialJump()
            } else {
                updateUiState()
            }
            syncProgressTracker()
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
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "media_playback_channel"
    }
}
