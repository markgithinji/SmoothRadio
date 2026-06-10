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
import com.smoothradio.radio.core.util.PlaybackConstants
import com.smoothradio.radio.service.util.proxy.LocalAudioProxy
import com.smoothradio.radio.service.util.metadata.MetadataUtils
import com.smoothradio.radio.service.util.command.ServiceCommand
import com.smoothradio.radio.service.util.command.ServiceCommandMapper
import com.smoothradio.radio.service.util.proxy.BufferEvictedException
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.concurrent.withLock
import kotlin.math.abs
import javax.inject.Inject

/**
 * A background service that manages audio streaming using ExoPlayer and Media3 MediaSession.
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
    lateinit var commandMapper: ServiceCommandMapper

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

    private var jumpToLiveOnReady = false
    private var playbackBaseTimeMs: Long = 0L
    private var activeStreamUrl: String? = null

    private fun getDroppedDurationMs(): Long {
        val dropped = localAudioProxy.totalBytesDropped
        val est = localAudioProxy.estimatedBytesPerMs.coerceAtLeast(1.0)
        val dur = (dropped / est).toLong()
        // Log.v("SmoothSeek", "getDroppedDurationMs: droppedBytes=$dropped, est=$est -> ${dur}ms")
        return dur
    }

    private fun getLoadedDurationMs(): Long = (localAudioProxy.totalBytesWritten / localAudioProxy.estimatedBytesPerMs).toLong()

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
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
                    if (isPreparingForAd) {
                        kotlinx.coroutines.delay(500)
                        continue
                    }

                    val pos = wrappedPlayer.currentPosition
                    val state = wrappedPlayer.playbackState
                    val isPlaying = wrappedPlayer.isPlaying
                    
                    val isBusy = jumpToLiveOnReady || state == Player.STATE_BUFFERING || (state == Player.STATE_READY && isPlaying)
                    
                    if (!isBusy && state == Player.STATE_IDLE && currentStationName == null) {
                        kotlinx.coroutines.delay(2000)
                        continue
                    }

                    if (pos > maxPositionReached) {
                        maxPositionReached = pos
                    }
                    
                    localAudioProxy.updateBitrateEstimation()

                    val droppedDur = getDroppedDurationMs()
                    val loadedDur = getLoadedDurationMs()

                    // Update metadata from proxy if available (handles Time Machine seeking)
                    val byteOffset = (pos * localAudioProxy.estimatedBytesPerMs).toLong()
                    val proxyMetadata = localAudioProxy.getMetadataForOffset(byteOffset)
                    
                    if (proxyMetadata != null) {
                        val cleaned = MetadataUtils.extractSongTitle(proxyMetadata)
                        if (cleaned.isNotEmpty() && cleaned != currentSongTitle) {
                            Log.d("SmoothMetadata", "New song detected: $cleaned")
                            onSongTitleChanged(cleaned)
                        }
                    }

                    // Seekbar reflects actual downloaded range [dropped, loaded]
                    val urlString = activeStreamUrl ?: ""
                    val isHls = urlString.contains(".m3u8") || urlString.contains("playlist")
                    val safetyBuffer = if (isHls) 12000L else 2000L

                    stateRepository.updatePosition(if (pos < 0) 0 else pos)
                    stateRepository.updateDuration(loadedDur)
                    stateRepository.updateMinPosition(droppedDur)
                    
                    val safeLoadedPos = (loadedDur - safetyBuffer).coerceAtLeast(droppedDur)
                    stateRepository.updateLoadedPosition(safeLoadedPos)

                    if (jumpToLiveOnReady || stateChange == StreamStates.BUFFERING || stateChange == StreamStates.PREPARING) {
                        val targetMs = PlaybackConstants.PROGRESS_TARGET_MS
                        val currentMs = localAudioProxy.totalBytesReceived.toDouble() / localAudioProxy.estimatedBytesPerMs.coerceAtLeast(1.0)
                        val progress = (currentMs / targetMs).toFloat().coerceIn(0f, 1f)
                        val displayProgress = if (progress > 0 || localAudioProxy.totalBytesReceived > 0) {
                            PlaybackConstants.PROGRESS_BASELINE + (progress * PlaybackConstants.PROGRESS_SCALE)
                        } else 0f
                        stateRepository.updateLoadingProgress(displayProgress)
                    } else {
                        stateRepository.updateLoadingProgress(1f)
                    }
                } catch (e: Exception) {
                    Log.e("SmoothSeek", "Error in progress update", e)
                }
                val currentState = wrappedPlayer.playbackState
                val isActuallyBusy = jumpToLiveOnReady || currentState == Player.STATE_BUFFERING || (currentState == Player.STATE_READY && wrappedPlayer.isPlaying)
                val delay = if (isActuallyBusy) 100L else 1000L
                kotlinx.coroutines.delay(delay)
            }
        }
    }

    private fun setupWrappedPlayer() {
        val basePlayer = castPlayer ?: player
        wrappedPlayer = object : ForwardingPlayer(basePlayer) {
            override fun getAvailableCommands(): Player.Commands {
                val builder = super.getAvailableCommands().buildUpon()
                    .remove(COMMAND_SEEK_TO_NEXT)
                    .remove(COMMAND_SEEK_TO_PREVIOUS)
                    .remove(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .remove(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .remove(COMMAND_SEEK_BACK)
                    .remove(COMMAND_SEEK_FORWARD)
                
                if (isPreparingForAd) {
                    builder.remove(COMMAND_PLAY_PAUSE)
                        .remove(COMMAND_STOP)
                } else {
                    builder.add(COMMAND_PLAY_PAUSE)
                        .add(COMMAND_STOP)
                }
                
                return builder.build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return getAvailableCommands().contains(command)
            }

            override fun getCurrentPosition(): Long {
                return playbackBaseTimeMs + super.getCurrentPosition()
            }

            override fun getDuration(): Long {
                val baseDur = super.getDuration()
                return if (baseDur > 0) baseDur else 24 * 60 * 60 * 1000L
            }

            override fun getMediaMetadata(): MediaMetadata {
                val metadata = super.getMediaMetadata()
                val stationName = currentStationName ?: getString(R.string.app_name)
                
                val currentStateLabel = when {
                    isPlaying -> getString(R.string.player_playing)
                    playbackState == Player.STATE_BUFFERING -> getString(R.string.player_buffering)
                    else -> stateChange.label.ifEmpty { getString(R.string.player_preparing_audio) }
                }

                val title = if (currentSongTitle.isNotEmpty()) currentSongTitle else currentStateLabel
                val subtitle = stationName
                
                return metadata.buildUpon()
                    .setTitle(title)
                    .setArtist(subtitle)
                    .setDisplayTitle(title)
                    .setSubtitle(subtitle)
                    .setAlbumTitle(stationName)
                    .build()
            }

            override fun getCurrentMediaItem(): MediaItem? {
                val item = super.getCurrentMediaItem() ?: return null
                val stationName = currentStationName ?: getString(R.string.app_name)
                
                val currentStateLabel = when {
                    isPlaying -> getString(R.string.player_playing)
                    playbackState == Player.STATE_BUFFERING -> getString(R.string.player_buffering)
                    else -> stateChange.label.ifEmpty { getString(R.string.player_preparing_audio) }
                }

                val title = if (currentSongTitle.isNotEmpty()) currentSongTitle else currentStateLabel
                val subtitle = stationName
                
                return item.buildUpon()
                    .setMediaMetadata(
                        item.mediaMetadata.buildUpon()
                            .setTitle(title)
                            .setArtist(subtitle)
                            .setDisplayTitle(title)
                            .setSubtitle(subtitle)
                            .build()
                    )
                    .build()
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

    private fun getCurrentStatusLabel(): String {
        return when {
            wrappedPlayer.isPlaying -> getString(R.string.player_playing)
            wrappedPlayer.playbackState == Player.STATE_BUFFERING -> getString(R.string.player_buffering)
            else -> stateChange.label
        }
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

        val stationName = currentStationName ?: getString(R.string.app_name)
        val currentStateLabel = getCurrentStatusLabel()

        // TITLE: Show Song Title if available, otherwise current state (Playing/Buffering)
        val title = if (currentSongTitle.isNotEmpty()) {
            currentSongTitle
        } else {
            currentStateLabel
        }

        // SUBTITLE: Always show Station Name
        val stationDisplay = stationName

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.notificationicon)
            .setContentTitle(title)
            .setContentText(stationDisplay)
            .setLargeIcon(getStationLogo())
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            
        Log.d("SmoothDebug", "Notification Build: Title=$title, Subtitle=$stationDisplay, isPlaying=${wrappedPlayer.isPlaying}, State=${wrappedPlayer.playbackState}")

        if (!isPreparingForAd) {
            builder.addAction(
                if (wrappedPlayer.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (wrappedPlayer.isPlaying) getString(R.string.player_pause) else getString(R.string.player_play),
                playPausePendingIntent
            )
        }

        builder.setStyle(
            mediaSession?.let {
                val style = androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(it)
                if (!isPreparingForAd) {
                    style.setShowActionsInCompactView(0)
                }
                style
            }
        )

        return builder.build()
    }

    private fun getStationLogo(): Bitmap? {
        return if (currentStationLogo != 0) {
            BitmapFactory.decodeResource(resources, currentStationLogo)
        } else null
    }

    private fun updateNotificationInternal() {
        val notification = createMediaStyleNotification()
        val stationName = currentStationName ?: "Unknown"
        val state = wrappedPlayer.playbackState
        val playing = wrappedPlayer.isPlaying
        
        Log.d("SmoothDebug", "!!! NOTIFY !!! Station: $stationName | Song: $currentSongTitle | PlayerState: $state | isPlaying: $playing")

        // Explicitly notify for all versions to ensure immediate UI updates (Fix for Android 14)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)

        notificationCallback?.onNotificationChanged(
            MediaNotification(NOTIFICATION_ID, notification)
        )
    }

    private fun refreshMediaSessionMetadata() {
        val stationName = currentStationName ?: getString(R.string.app_name)
        val currentStateLabel = getCurrentStatusLabel()

        val title = if (currentSongTitle.isNotEmpty()) currentSongTitle else currentStateLabel
        val subtitle = stationName
        
        Log.d("SmoothDebug", "Refreshing MediaSession Metadata: Title=$title, Subtitle=$subtitle")

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(subtitle)
            .setDisplayTitle(title)
            .setSubtitle(subtitle)
            .setAlbumTitle(stationName)
            .build()
            
        // 1. Update playlist metadata
        wrappedPlayer.playlistMetadata = metadata
        
        // 2. Update current media item metadata (Fix for Android 11 media controls)
        wrappedPlayer.currentMediaItem?.let { item ->
            val updatedItem = item.buildUpon()
                .setMediaMetadata(
                    item.mediaMetadata.buildUpon()
                        .setTitle(title)
                        .setArtist(subtitle)
                        .build()
                )
                .build()
            
            wrappedPlayer.replaceMediaItem(wrappedPlayer.currentMediaItemIndex, updatedItem)
        }
    }

    private fun onSongTitleChanged(newTitle: String) {
        val stationName = currentStationName ?: getString(R.string.app_name)
        
        // Define status labels to ignore if they appear in metadata events
        val statusLabels = listOf(
            getString(R.string.player_playing),
            getString(R.string.player_buffering),
            getString(R.string.player_preparing_audio)
        )
        
        // 1. Ignore if it's a status label (prevents circular feedback loop)
        if (statusLabels.any { it.equals(newTitle, ignoreCase = true) }) {
            return
        }

        // 2. If the "song" metadata is just the station name, ignore it (Fix for double name issue)
        if (newTitle == currentSongTitle || newTitle.equals(stationName, ignoreCase = true)) {
            if (newTitle.equals(stationName, ignoreCase = true) && currentSongTitle.isNotEmpty()) {
                Log.d("SmoothDebug", "Song cleared: Metadata matches station name")
                currentSongTitle = ""
                stateRepository.updateMetadata("")
                refreshMediaSessionMetadata()
                updateNotificationInternal()
            }
            return
        }
        
        Log.d("SmoothDebug", "Song Changed: '$currentSongTitle' -> '$newTitle'")
        currentSongTitle = newTitle
        stateRepository.updateMetadata(newTitle)
        
        refreshMediaSessionMetadata()
        updateNotificationInternal()
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
        if (command is ServiceCommand.None) return

        // Global updates for metadata-bearing commands
        when (command) {
            is ServiceCommand.Start -> {
                currentStationName = command.name
                currentStationLogo = command.logo
                stateRepository.updateStationName(command.name ?: "")
            }
            is ServiceCommand.ShowAd -> {
                currentStationName = command.name
                currentStationLogo = command.logo
                stateRepository.updateStationName(command.name ?: "")
            }
            else -> {}
        }

        if (command is ServiceCommand.Start || command is ServiceCommand.ShowAd || command is ServiceCommand.Stop) {
            updateNotificationInternal()
        }

        when (command) {
            is ServiceCommand.Start -> {
                Log.d("StreamService", " ACTION_START → ${currentStationName}")
                isPreparingForAd = false
                maxPositionReached = 0L
                currentSongTitle = ""
                playbackBaseTimeMs = 0L
                activeStreamUrl = command.link
                
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
                maxPositionReached = 0L
                currentSongTitle = ""
                playbackBaseTimeMs = 0L
                activeStreamUrl = command.link
                
                if (command.link.isNotEmpty()) {
                    localAudioProxy.start(command.link)
                }
                
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
                activeStreamUrl = null
                wrappedPlayer.pause()
                wrappedPlayer.stop()
                wrappedPlayer.clearMediaItems()
                currentSongTitle = ""
                
                stateRepository.updatePosition(0L)
                stateRepository.updateDuration(0L)
                stateRepository.updateMinPosition(0L)
                stateRepository.updateLoadedPosition(0L)

                setState(StreamStates.IDLE)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            is ServiceCommand.Play -> {
                if (isPreparingForAd) return
                // If we have a media item and are just paused, try to resume first.
                // If the position is evicted, the onPlayerError handler will auto-seek to live.
                if (wrappedPlayer.mediaItemCount > 0 && wrappedPlayer.playbackState != Player.STATE_IDLE) {
                    wrappedPlayer.play()
                } else {
                    val loadedDur = getLoadedDurationMs()
                    val urlString = activeStreamUrl ?: ""
                    val isHls = urlString.contains(".m3u8") || urlString.contains("playlist")
                    val safetyBuffer = if (isHls) 12000L else 4000L
                    seekToAbsolute((loadedDur - safetyBuffer).coerceAtLeast(0))
                }
            }
            is ServiceCommand.Pause -> {
                if (isPreparingForAd) return
                wrappedPlayer.pause()
            }
            is ServiceCommand.SeekBack -> {
                val current = wrappedPlayer.currentPosition
                val droppedDur = getDroppedDurationMs()
                val target = (current - PlaybackConstants.SEEK_INCREMENT_MS).coerceAtLeast(droppedDur)
                seekToAbsolute(target)
            }
            is ServiceCommand.SeekForward -> {
                val current = wrappedPlayer.currentPosition
                val loadedDur = getLoadedDurationMs()
                val urlString = activeStreamUrl ?: ""
                val isHls = urlString.contains(".m3u8") || urlString.contains("playlist")
                val safetyBuffer = if (isHls) PlaybackConstants.HLS_SAFETY_BUFFER_MS else PlaybackConstants.PROGRESSIVE_SAFETY_BUFFER_MS * 2
                val target = (current + PlaybackConstants.SEEK_INCREMENT_MS).coerceAtMost(loadedDur - safetyBuffer)
                seekToAbsolute(target)
            }
            is ServiceCommand.SeekTo -> {
                val loadedDur = getLoadedDurationMs()
                val droppedDur = getDroppedDurationMs()
                val urlString = activeStreamUrl ?: ""
                val isHls = urlString.contains(".m3u8") || urlString.contains("playlist")
                val safetyBuffer = if (isHls) PlaybackConstants.HLS_SAFETY_BUFFER_MS else PlaybackConstants.PROGRESSIVE_SAFETY_BUFFER_MS * 2
                val target = command.position.coerceIn(droppedDur, (loadedDur - safetyBuffer).coerceAtLeast(droppedDur))
                seekToAbsolute(target)
            }
            is ServiceCommand.SetEqBand -> {
                if (command.band != -1) setEqualizerBand(command.band, command.level)
            }
            else -> {}
        }
    }

    private fun setState(newState: StreamStates) {
        if ((newState == StreamStates.PREPARING || newState == StreamStates.BUFFERING) && activeStreamUrl == null) {
            return
        }
        if (stateChange == newState) return
        stateChange = newState
        stateRepository.updateState(newState)
        updateNotificationInternal()
    }

    private fun play(link: String) {
        if (link.isEmpty()) {
            setState(StreamStates.IDLE)
            return
        }

        if (activeStreamUrl == link && (wrappedPlayer.playbackState == Player.STATE_READY || wrappedPlayer.playbackState == Player.STATE_BUFFERING)) {
            isPreparingForAd = false
            wrappedPlayer.playWhenReady = true
            performInitialJump()
            return
        }

        isPreparingForAd = false
        activeStreamUrl = link
        playbackBaseTimeMs = 0L
        
        val isHls = link.contains(".m3u8") || link.contains("playlist")
        jumpToLiveOnReady = isHls
        
        wrappedPlayer.playWhenReady = true
        preparePlayer(link.toUri())
    }

    private fun preparePlayer(uri: Uri) {
        wrappedPlayer.stop()
        maxPositionReached = 0L
        jumpToLiveOnReady = true
        playbackBaseTimeMs = 0L
        
        val uriString = uri.toString()
        val isHls = uriString.contains(".m3u8") || uriString.contains("playlist")

        if (!localAudioProxy.isStartedFor(uriString)) {
            localAudioProxy.start(uriString)
        }

        val proxyUri = "proxy://smoothradio/stream?byteOffset=0".toUri()
        val cacheKey = currentStationName ?: uriString
        
        val stationName = currentStationName ?: getString(R.string.app_name)
        val initialMetadata = MediaMetadata.Builder()
            .setTitle(stationName)
            .setArtist(stationName)
            .build()

        val mimeType = if (isHls) MimeTypes.AUDIO_AAC else MimeTypes.AUDIO_MPEG

        val mediaItem = MediaItem.Builder()
            .setUri(proxyUri)
            .setMimeType(mimeType)
            .setCustomCacheKey(cacheKey)
            .setMediaMetadata(initialMetadata)
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(2000)
                    .build()
            )
            .build()
        wrappedPlayer.setMediaItem(mediaItem)
        wrappedPlayer.prepare()
    }

    private fun seekToAbsolute(positionMs: Long) {
        val urlString = activeStreamUrl ?: return
        val isHls = urlString.contains(".m3u8") || urlString.contains("playlist")

        // Get current buffer bounds
        val minValidMs = getDroppedDurationMs()  // Start of buffer (oldest available)
        val maxValidMs = getLoadedDurationMs()    // End of buffer (newest available)

        Log.d("SmoothSeek", "seekToAbsolute: Requested=${positionMs}ms. Buffer range: ${minValidMs}ms to ${maxValidMs}ms")

        // Calculate target byte position
        val targetByte = (positionMs * localAudioProxy.estimatedBytesPerMs).toLong()
        val minValidByte = localAudioProxy.totalBytesDropped
        val maxValidByte = localAudioProxy.totalBytesWritten

        // CRITICAL: Clamp position to valid range
        val clampedPosition = when {
            positionMs < minValidMs + 1000 -> {
                val safeMin = minValidMs + 1000
                Log.w("SmoothSeek", "seekToAbsolute: Position ${positionMs}ms is too close to buffer start (${minValidMs}ms). Seeking to safe start: ${safeMin}ms.")
                safeMin
            }
            positionMs > maxValidMs - 5000 -> { // Leave 5 second safety margin
                val safePos = (maxValidMs - 5000).coerceAtLeast(minValidMs)
                Log.w("SmoothSeek", "seekToAbsolute: Position ${positionMs}ms is near buffer end. Seeking to ${safePos}ms")
                safePos
            }
            else -> positionMs
        }

        // Recalculate byte offset from clamped position
        val clampedByte = (clampedPosition * localAudioProxy.estimatedBytesPerMs).toLong()

        Log.d("SmoothSeek", "seekToAbsolute: Clamped to ${clampedPosition}ms ($clampedByte bytes)")

        // If we're already within a reasonable distance of the target, don't seek
        val currentPosition = wrappedPlayer.currentPosition
        if (abs(currentPosition - clampedPosition) < 3000) {
            Log.d("SmoothSeek", "seekToAbsolute: Already within 3 seconds of target, skipping seek")
            wrappedPlayer.play()
            return
        }

        val proxyUri = "proxy://smoothradio/stream?byteOffset=$clampedByte".toUri()
        val mimeType = if (isHls) MimeTypes.AUDIO_AAC else MimeTypes.AUDIO_MPEG
        val cacheKey = currentStationName ?: urlString

        val mediaItem = MediaItem.Builder()
            .setUri(proxyUri)
            .setMimeType(mimeType)
            .setCustomCacheKey(cacheKey)
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(2000)
                    .build()
            )
            .build()

        playbackBaseTimeMs = clampedPosition
        wrappedPlayer.setMediaItem(mediaItem, true)
        wrappedPlayer.prepare()
        wrappedPlayer.play()
        stateRepository.updatePosition(clampedPosition)
    }

    private fun prepareShowAd(link: String) {
        if (link.isEmpty()) {
            wrappedPlayer.stop()
            wrappedPlayer.clearMediaItems()
            isPlaying = false
            updateNotificationInternal()
            return
        }

        maxPositionReached = 0L
        jumpToLiveOnReady = true
        playbackBaseTimeMs = 0L
        
        val uriString = link
        val isHls = uriString.contains(".m3u8") || uriString.contains("playlist")
        val proxyUri = "proxy://smoothradio/stream?byteOffset=0".toUri()
        val cacheKey = currentStationName ?: uriString
        val mimeType = if (isHls) MimeTypes.AUDIO_AAC else MimeTypes.AUDIO_MPEG

        val stationName = currentStationName ?: getString(R.string.app_name)
        val initialMetadata = MediaMetadata.Builder()
            .setTitle(stationName)
            .setArtist(stationName)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(proxyUri)
            .setMimeType(mimeType)
            .setCustomCacheKey(cacheKey)
            .setMediaMetadata(initialMetadata)
            .build()
            
        wrappedPlayer.playWhenReady = false
        wrappedPlayer.setMediaItem(mediaItem)
        wrappedPlayer.prepare()
        
        isPlaying = false
        updateNotificationInternal()
    }

    private fun performInitialJump() {
        if (!jumpToLiveOnReady || isPreparingForAd) return
        
        val urlString = activeStreamUrl ?: ""
        val isHls = urlString.contains(".m3u8") || urlString.contains("playlist")
        
        if (isHls) {
            jumpToLiveOnReady = false
            val loadedDur = getLoadedDurationMs()
            val target = (loadedDur - PlaybackConstants.HLS_SAFETY_BUFFER_MS).coerceAtLeast(0L)
            seekToAbsolute(target)
        } else if (wrappedPlayer.playWhenReady) {
            jumpToLiveOnReady = false
            wrappedPlayer.play()
            updateUiState()
        }
    }

    private fun setupEqualizer(sessionId: Int) {
        if (sessionId == 0 || sessionId == audioSessionId) return
        audioSessionId = sessionId
        try {
            equalizer?.release()
            equalizer = Equalizer(0, sessionId).apply {
                val bands = numberOfBands
                serviceScope.launch {
                    // Small delay to let the audio session stabilize
                    kotlinx.coroutines.delay(500)
                    
                    var hasActiveSettings = false
                    for (i in 0 until bands) {
                        val level = equalizerRepository.getBandLevel(i)
                        if (level != 0.toShort()) {
                            try {
                                setBandLevel(i.toShort(), level)
                                hasActiveSettings = true
                            } catch (e: Exception) {
                                Log.e("StreamService", "Failed to apply EQ band $i", e)
                            }
                        }
                    }
                    // Enable ONLY after bands are configured to prevent volume jump
                    if (hasActiveSettings) {
                        enabled = true
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
        localAudioProxy.stop()
        equalizer?.release()
        equalizer = null
        wrappedPlayer.removeListener(exoplayerEventListener)
        wrappedPlayer.release()
        mediaSession?.release()
        mediaSession = null
        isPlaying = false
        stateChange = StreamStates.IDLE
        stateRepository.updateState(StreamStates.IDLE)
        stateRepository.updatePosition(0L)
        stateRepository.updateDuration(0L)
        stateRepository.updateMinPosition(0L)
        stateRepository.updateLoadedPosition(0L)
        stateRepository.updateMetadata("")
        isPreparingForAd = false
        unregisterTimerReceivers()
        serviceScope.cancel()
        super.onDestroy()
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
        override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo {
            return MediaNotification.Provider.NotificationChannelInfo(CHANNEL_ID, getString(R.string.notification_channel_name))
        }
    }

    inner class StopPlayFromTimerReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Toast.makeText(context, getString(R.string.stopped), Toast.LENGTH_SHORT).show()
            stopSelf()
        }
    }

    inner class SetStopTimerReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val timeInMillis = intent.getLongExtra(ServiceCommand.EXTRA_TIME_IN_MILLIS, 0)
            val alarmPendingIntent = PendingIntent.getBroadcast(this@StreamService, 0, Intent(ServiceCommand.ACTION_STOP_FROM_TIMER).setPackage(packageName), PendingIntent.FLAG_IMMUTABLE)
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, timeInMillis, alarmPendingIntent)
        }
    }

    private fun updateUiState() {
        if (isPreparingForAd) return
        val playbackState = wrappedPlayer.playbackState
        val isPlayerPlaying = wrappedPlayer.isPlaying
        val playWhenReady = wrappedPlayer.playWhenReady
        
        val newState = when {
            playbackState == Player.STATE_BUFFERING -> StreamStates.BUFFERING
            playbackState == Player.STATE_READY && isPlayerPlaying -> StreamStates.PLAYING
            playbackState == Player.STATE_READY && !isPlayerPlaying -> StreamStates.IDLE
            playbackState == Player.STATE_ENDED -> StreamStates.ENDED
            else -> StreamStates.IDLE
        }
        
        if (stateChange != newState) {
            Log.d("SmoothMetadata", "updateUiState: $stateChange -> $newState (state=$playbackState, playing=$isPlayerPlaying, pwr=$playWhenReady)")
            setState(newState)
        }
    }

    inner class EventListener : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            setupEqualizer(audioSessionId)
        }
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            val rawTitle = mediaMetadata.title?.toString() ?: ""
            val cleaned = MetadataUtils.extractSongTitle(rawTitle)
            Log.d("SmoothMetadata", "onMediaMetadataChanged: raw=$rawTitle -> cleaned=$cleaned")
            if (cleaned.isNotEmpty() && currentSongTitle != cleaned) {
                onSongTitleChanged(cleaned)
            }
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d("SmoothDebug", "onIsPlayingChanged: $isPlaying (State: ${wrappedPlayer.playbackState})")
            this@StreamService.isPlaying = isPlaying
            updateUiState()
            refreshMediaSessionMetadata() // Ensure MediaSession is updated when play/pause changes
            updateNotificationInternal()
        }
        override fun onPlayerError(error: PlaybackException) {
            jumpToLiveOnReady = false

            // AUTO-SEEK ON EVICTION: If we hit a BufferEvictedException (history lost during pause),
            // automatically seek to the start of the buffer + a small safety margin
            if (error.cause is BufferEvictedException) {
                val evicted = error.cause as BufferEvictedException
                Log.e("SmoothSeek", "onPlayerError: BufferEvictedException at ${evicted.evictedPositionMs}ms")

                // Don't use the byte offset from the exception directly
                // Instead, get the current buffer start and add a small offset
                val bufferStartMs = getDroppedDurationMs()
                val safetyOffset = 1000L // Start 1 second into the buffer to avoid eviction edge
                val newPositionMs = bufferStartMs + safetyOffset

                Log.w("SmoothSeek", "EventListener: Buffer evicted. " +
                        "Buffer start: ${bufferStartMs}ms, " +
                        "Seeking to: ${newPositionMs}ms " +
                        "(evicted from: ${evicted.evictedPositionMs}ms)")

                // Update UI state to show buffering during seek
                setState(StreamStates.BUFFERING)

                // Perform the seek to the new position (NOT the byte offset from exception)
                seekToAbsolute(newPositionMs)
                return
            }

            // IGNORE ERRORS AFTER STOP: If the user explicitly stopped the radio, suppress
            // any delayed connection or eviction errors to prevent UI flicker.
            if (activeStreamUrl == null) {
                Log.d("StreamService", "Suppressing error after explicit stop: ${error.cause?.message}")
                return
            }

            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
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
                Player.STATE_BUFFERING -> "BUFFERING"
                Player.STATE_READY -> "READY"
                Player.STATE_IDLE -> "IDLE"
                Player.STATE_ENDED -> "ENDED"
                else -> "UNKNOWN"
            }
            Log.d("SmoothDebug", "onPlaybackStateChanged: $stateName (isPlaying: ${wrappedPlayer.isPlaying})")

            if (state == Player.STATE_READY && jumpToLiveOnReady && !isPreparingForAd) {
                performInitialJump()
            }
            updateUiState()
            refreshMediaSessionMetadata() // Sync MediaSession with new state
            updateNotificationInternal() // Sync Notification with new state
        }
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            Log.d("SmoothSeek", "Position Discontinuity: Reason=$reason, Old=${oldPosition.positionMs}, New=${newPosition.positionMs}")
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "media_playback_channel"
    }
}
