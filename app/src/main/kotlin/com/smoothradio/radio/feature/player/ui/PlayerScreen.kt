package com.smoothradio.radio.feature.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.smoothradio.radio.R
import com.smoothradio.radio.core.domain.model.StreamStates
import com.smoothradio.radio.core.ui.PlayerControlViewModel
import com.smoothradio.radio.core.ui.StationUiState
import com.smoothradio.radio.core.ui.common.AdBanner
import com.smoothradio.radio.core.ui.common.DotLoadingAnimation
import com.smoothradio.radio.core.ui.common.SimpleTopBar
import com.smoothradio.radio.core.ui.util.LogoMapper

@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    playerControlViewModel: PlayerControlViewModel = hiltViewModel()
) {
    val stationUiState by playerControlViewModel.stationUiState.collectAsStateWithLifecycle()
    val playbackState by playerControlViewModel.playbackState.collectAsStateWithLifecycle()
    val metadata by playerControlViewModel.metadata.collectAsStateWithLifecycle()
    val position by playerControlViewModel.position.collectAsStateWithLifecycle()
    val duration by playerControlViewModel.duration.collectAsStateWithLifecycle()
    val minPosition by playerControlViewModel.minPosition.collectAsStateWithLifecycle()
    val loadedPosition by playerControlViewModel.loadedPosition.collectAsStateWithLifecycle()
    val colorScheme = MaterialTheme.colorScheme

    var showSleepDialog by remember { mutableStateOf(false) }
    var showEqDialog by remember { mutableStateOf(false) }

    val currentStation = stationUiState.station ?: return EmptyPlayerContent(
        modifier = modifier,
        colorScheme = colorScheme
    )

    val animatedColor by animateColorAsState(
        targetValue = when (playbackState) {
            is StreamStates.PLAYING -> colorScheme.primary.copy(alpha = 0.15f)
            is StreamStates.BUFFERING, is StreamStates.PREPARING -> colorScheme.tertiary.copy(
                alpha = 0.15f
            )

            else -> colorScheme.surfaceVariant
        },
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "background color"
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        val screenWidth = maxWidth
        // Switch to landscape earlier (at 90% width) for a better desktop/foldable experience
        val isLandscape = screenWidth > (screenHeight * 0.9f)

        // Responsive Layout Configuration
        val layoutConfig = remember(screenHeight, screenWidth, isLandscape) {
            val isTinyCompact = if (isLandscape) screenHeight < 260.dp else screenHeight < 200.dp

            // In landscape, we keep buttons compact
            val isCompact = isLandscape || screenHeight in 200.dp..380.dp
            val isShrinking = !isLandscape && screenHeight in 380.dp..425.dp

            val isMedium = screenHeight in 426.dp..550.dp
            val isWidePortrait = !isLandscape && screenWidth > 500.dp

            val logoVisibility = when {
                isLandscape -> if (screenHeight > 280.dp) 1f else 0f
                screenHeight >= 595.dp -> 1f
                screenHeight <= 370.dp -> 0f
                else -> {
                    // Interpolate between 595dp (100% size) and 370dp (0% size)
                    val range = 595f - 370f
                    val progress = (screenHeight.value - 370f) / range
                    progress.coerceIn(0f, 1f)
                }
            }

            val playButtonSize = when {
                isTinyCompact -> 48.dp
                isCompact -> 56.dp
                isShrinking -> {
                    val range = 425f - 300f
                    val progress = (screenHeight.value - 300f) / range
                    (48 + progress * 32).dp
                }

                else -> 80.dp
            }

            object {
                val showAd = if (isLandscape) screenHeight > 420.dp else screenHeight > 570.dp
                val showSecondRow =
                    if (isLandscape) screenHeight >= 500.dp else screenHeight > 720.dp
                val showMetadata = if (isLandscape) screenHeight > 300.dp else screenHeight > 420.dp
                val showSeekBar = screenHeight >= 770.dp
                val logoAlpha = logoVisibility
                val tinyCompact = isTinyCompact
                val compact = isCompact
                val shrinking = isShrinking
                val btnSize = playButtonSize
                val landscape = isLandscape

                val horizontalPadding = when {
                    isTinyCompact || isCompact -> 8.dp
                    isShrinking -> 12.dp
                    isMedium -> 16.dp
                    else -> 24.dp
                }

                val topPadding = when {
                    isTinyCompact || isCompact -> 4.dp
                    isShrinking -> 6.dp
                    isMedium -> 8.dp
                    else -> 12.dp
                }

                val baseLogoScale = when {
                    isLandscape -> 0.45f
                    isWidePortrait -> 0.5f
                    isShrinking -> 0.4f
                    isMedium -> 0.52f
                    else -> 0.62f
                }

                // If ad is shown on a screen that isn't very tall, shrink the logo further to compensate
                val logoScale = (if (showAd && !isLandscape && screenHeight < 750.dp) {
                    baseLogoScale * 0.85f
                } else {
                    baseLogoScale
                }) * logoVisibility
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            SimpleTopBar(
                title = stringResource(R.string.player_live_tag),
                actionIcon = {
                    IconButton(onClick = { showEqDialog = true }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_toolbar_eq),
                            contentDescription = stringResource(R.string.player_equalizer),
                            tint = colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .drawBehind {
                        drawRect(
                            Brush.verticalGradient(
                                listOf(animatedColor, colorScheme.background),
                                startY = 0f,
                                endY = size.height
                            )
                        )
                    }
            ) {
                if (layoutConfig.landscape) {
                    // Landscape layout for tablets/phones
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = layoutConfig.horizontalPadding)
                            .padding(top = layoutConfig.topPadding, bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (layoutConfig.logoAlpha > 0f) {
                            // Left side: Logo Section
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                PlayerLogoSection(
                                    stationUiState = stationUiState,
                                    playbackState = playbackState,
                                    modifier = Modifier
                                        .fillMaxHeight(0.9f)
                                        .aspectRatio(1f)
                                )
                            }
                            Spacer(modifier = Modifier.width(24.dp))
                        }

                        // Info and Controls
                        Column(
                            modifier = if (layoutConfig.logoAlpha > 0f) Modifier.weight(1.2f) else Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (!layoutConfig.tinyCompact) {
                                StationHeader(
                                    stationName = currentStation.stationName,
                                    playbackState = playbackState,
                                    isCompact = layoutConfig.compact,
                                    isShrinking = layoutConfig.shrinking,
                                    colorScheme = colorScheme
                                )

                                if (layoutConfig.showMetadata) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Box(
                                        modifier = Modifier
                                            .height(48.dp)
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (playbackState is StreamStates.PLAYING && metadata.isNotEmpty()) {
                                            AnimatedMetadataWithMarquee(
                                                metadata = metadata,
                                                isVisible = true
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))

                                if (layoutConfig.showSeekBar) {
                                    AudioSeekBar(
                                        position = position,
                                        duration = duration,
                                        minPosition = minPosition,
                                        loadedPosition = loadedPosition,
                                        playbackState = playbackState,
                                        onSeek = { playerControlViewModel.seekTo(it) },
                                        colorScheme = colorScheme
                                    )

                                    Spacer(modifier = Modifier.height(24.dp))
                                }
                            }

                            PlaybackControlRow(
                                playbackState = playbackState,
                                playButtonSize = layoutConfig.btnSize,
                                isTinyCompact = layoutConfig.tinyCompact,
                                isCompact = layoutConfig.compact,
                                onPrevious = { playerControlViewModel.requestPreviousStation() },
                                onNext = { playerControlViewModel.requestNextStation() },
                                onPlayPause = { playerControlViewModel.togglePlayPause() },
                                onSeekBack = { playerControlViewModel.seekBack() },
                                onSeekForward = { playerControlViewModel.seekForward() },
                                colorScheme = colorScheme
                            )

                            if (layoutConfig.showSecondRow) {
                                Spacer(modifier = Modifier.height(24.dp))
                                ActionButtonsRow(
                                    onRefresh = { playerControlViewModel.requestRefresh() },
                                    onSleepClick = { showSleepDialog = true },
                                    colorScheme = colorScheme
                                )
                            }

                            if (layoutConfig.showAd) {
                                Spacer(modifier = Modifier.height(24.dp))
                                AdBanner()
                            }
                        }
                    }
                } else {
                    // Portrait layout
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = layoutConfig.horizontalPadding)
                            .padding(
                                top = layoutConfig.topPadding,
                                bottom = 4.dp
                            ),
                        verticalArrangement = if (layoutConfig.tinyCompact || layoutConfig.compact) Arrangement.Center else Arrangement.Top
                    ) {
                        // Top Section (Logo, Station, Metadata)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.wrapContentHeight()
                        ) {
                            // Logo Section
                            if (layoutConfig.logoAlpha > 0f) {
                                PlayerLogoSection(
                                    stationUiState = stationUiState,
                                    playbackState = playbackState,
                                    modifier = Modifier
                                        .fillMaxWidth(layoutConfig.logoScale)
                                        .fillMaxHeight(if (layoutConfig.showAd) 0.33f else 0.38f)
                                        .sizeIn(maxWidth = 400.dp, maxHeight = 400.dp)
                                        .aspectRatio(1f)
                                )
                                Spacer(
                                    modifier = Modifier.height(
                                        (if (layoutConfig.showAd) 4.dp else if (layoutConfig.shrinking) 8.dp else 12.dp) * layoutConfig.logoAlpha
                                    )
                                )
                            }

                            // Station Name & Header
                            if (!layoutConfig.tinyCompact) {
                                StationHeader(
                                    stationName = currentStation.stationName,
                                    playbackState = playbackState,
                                    isCompact = layoutConfig.compact,
                                    isShrinking = layoutConfig.shrinking,
                                    colorScheme = colorScheme
                                )
                            }

                            // Metadata
                            if (layoutConfig.showMetadata && !layoutConfig.tinyCompact) {
                                Spacer(
                                    modifier = Modifier.height(
                                        if (layoutConfig.compact) 4.dp else 6.dp
                                    )
                                )
                                Box(
                                    modifier = Modifier
                                        .height(if (layoutConfig.compact) 40.dp else 44.dp)
                                        .fillMaxWidth(0.85f)
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (playbackState is StreamStates.PLAYING && metadata.isNotEmpty()) {
                                        AnimatedMetadataWithMarquee(
                                            metadata = metadata,
                                            isVisible = true
                                        )
                                    }
                                }
                            }
                        }

                        // Flexible spacer between Top and Bottom sections
                        Spacer(modifier = Modifier.weight(1f))

                        // Bottom Section (Seekbar, Controls, Ad)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.wrapContentHeight()
                        ) {
                            if (layoutConfig.showSeekBar) {
                                AudioSeekBar(
                                    position = position,
                                    duration = duration,
                                    minPosition = minPosition,
                                    loadedPosition = loadedPosition,
                                    playbackState = playbackState,
                                    onSeek = { playerControlViewModel.seekTo(it) },
                                    colorScheme = colorScheme
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            // Playback Controls
                            PlaybackControlRow(
                                playbackState = playbackState,
                                playButtonSize = layoutConfig.btnSize,
                                isTinyCompact = layoutConfig.tinyCompact,
                                isCompact = layoutConfig.compact,
                                onPrevious = { playerControlViewModel.requestPreviousStation() },
                                onNext = { playerControlViewModel.requestNextStation() },
                                onPlayPause = {
                                    playerControlViewModel.togglePlayPause()
                                },
                                onSeekBack = { playerControlViewModel.seekBack() },
                                onSeekForward = { playerControlViewModel.seekForward() },
                                colorScheme = colorScheme
                            )

                            // Secondary controls
                            if (layoutConfig.showSecondRow) {
                                Spacer(modifier = Modifier.height(if (layoutConfig.showAd) 12.dp else 16.dp))
                                ActionButtonsRow(
                                    onRefresh = { playerControlViewModel.requestRefresh() },
                                    onSleepClick = { showSleepDialog = true },
                                    colorScheme = colorScheme
                                )
                            }

                            // Ad
                            if (layoutConfig.showAd) {
                                Spacer(modifier = Modifier.height(if (layoutConfig.compact) 8.dp else 16.dp))
                                AdBanner()
                            }
                        }

                        // Fixed bottom clearance
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }
    }

    // Dialogs
    if (showEqDialog) {
        val eqLevels by playerControlViewModel.eqBandLevels.collectAsStateWithLifecycle()
        EqualizerDialog(
            currentLevels = eqLevels,
            onDismiss = { showEqDialog = false },
            onBandChange = { band, level ->
                playerControlViewModel.setEqualizerBand(band, level)
            }
        )
    }

    if (showSleepDialog) {
        SleepTimerDialog(
            onDismiss = { showSleepDialog = false },
            onConfirm = { minutes ->
                showSleepDialog = false
                playerControlViewModel.setSleepTimer(minutes)
            }
        )
    }
}

@Composable
fun StationHeader(
    stationName: String,
    playbackState: StreamStates,
    isCompact: Boolean,
    isShrinking: Boolean,
    colorScheme: ColorScheme
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stationName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            fontSize = when {
                isCompact -> 16.sp
                isShrinking -> 18.sp
                else -> 24.sp
            },
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(if (isCompact || isShrinking) 2.dp else 8.dp))
        Box(
            modifier = Modifier.height(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                when (playbackState) {
                    is StreamStates.PLAYING -> Text(
                        text = stringResource(R.string.player_now_playing),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.primary
                    )

                    is StreamStates.PAUSED -> Text(
                        text = stringResource(R.string.player_paused),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurfaceVariant
                    )

                    is StreamStates.BUFFERING, is StreamStates.PREPARING -> {
                        DotLoadingAnimation(
                            dotSize = if (isCompact || isShrinking) 6.dp else 8.dp,
                            dotSpacing = if (isCompact || isShrinking) 4.dp else 6.dp,
                            color = colorScheme.tertiary,
                            animationDelay = 200,
                            animationDuration = 400
                        )
                        Spacer(modifier = Modifier.width(if (isCompact || isShrinking) 6.dp else 8.dp))
                        Text(
                            text = stringResource(R.string.player_buffering),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = if (isCompact || isShrinking) 9.sp else 10.sp,
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.tertiary
                        )
                    }

                    else -> {}
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSeekBar(
    position: Long,
    duration: Long,
    minPosition: Long,
    loadedPosition: Long,
    playbackState: StreamStates,
    onSeek: (Long) -> Unit,
    colorScheme: ColorScheme
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var isDraggingManual by remember { mutableStateOf(false) }

    // Track when the last seek occurred to prevent "jumping" back to the old position
    // while the player is processing the seek request.
    var lastSeekTimestamp by remember { mutableLongStateOf(0L) }
    val isInteracting = isPressed || isDraggingManual

    // The current window size (total buffer length in ms)
    val windowSize = (duration - minPosition).coerceAtLeast(1L)

    // We use a fixed 0..1 scale for the Slider to ensure absolute coordinate stability
    var sliderFraction by remember { mutableFloatStateOf(0f) }

    // Lock the window values when interaction starts to ensure the mapping stays stable
    val lockedWindow = remember(isInteracting) {
        if (isInteracting) minPosition to windowSize else null
    }

    // Sync fraction with absolute position ONLY when not interacting and not recently seeked
    LaunchedEffect(position, minPosition, windowSize, isInteracting) {
        val now = System.currentTimeMillis()
        // If we are interacting, don't sync.
        // If we just seeked (< 1 second ago), don't sync to avoid the "jump" while backend updates.
        if (!isInteracting && (now - lastSeekTimestamp > 1000L)) {
            sliderFraction =
                ((position - minPosition).toFloat() / windowSize.toFloat()).coerceIn(0f, 1f)
        }
    }

    val isInteractive = playbackState !is StreamStates.IDLE && playbackState !is StreamStates.ENDED

    val haptic = LocalHapticFeedback.current

    val thumbSize by animateDpAsState(
        targetValue = if (isInteracting) 18.dp else 12.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "thumbSize"
    )

    val trackHeight by animateDpAsState(
        targetValue = if (isInteracting) 8.dp else 4.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "trackHeight"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .graphicsLayer { alpha = if (isInteractive) 1f else 0.5f }
    ) {
        Slider(
            enabled = isInteractive,
            interactionSource = interactionSource,
            value = sliderFraction,
            onValueChange = {
                if (!isDraggingManual) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                isDraggingManual = true
                sliderFraction = it
            },
            onValueChangeFinished = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                // Map the 0..1 fraction back to an absolute timestamp using the locked window
                val currentMin = lockedWindow?.first ?: minPosition
                val currentWidth = lockedWindow?.second ?: windowSize
                val absoluteSeekTarget = currentMin + (sliderFraction * currentWidth).toLong()

                lastSeekTimestamp = System.currentTimeMillis()
                onSeek(absoluteSeekTarget)
                isDraggingManual = false
            },
            valueRange = 0f..1f,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            thumb = {
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isInteractive) {
                        // Main thumb (Green core with White border)
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            shadowElevation = if (isInteracting) 4.dp else 2.dp,
                            modifier = Modifier.size(thumbSize)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(2.5.dp)
                                    .background(colorScheme.primary, CircleShape)
                            )
                        }
                    }
                }
            },
            track = { sliderState ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(trackHeight)
                            .clip(CircleShape)
                    ) {
                        val loadedFraction =
                            ((loadedPosition - minPosition).toFloat() / windowSize.toFloat()).coerceIn(
                                0f,
                                1f
                            )

                        // 1. Background (Full Window)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .background(colorScheme.onSurface.copy(alpha = 0.1f))
                        )

                        // 2. Buffer Progress
                        if (isInteractive) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(loadedFraction)
                                    .fillMaxHeight()
                                    .background(colorScheme.secondary.copy(alpha = 0.30f))
                            )
                        }

                        // 3. Active Seek Track with Gradient
                        val activeFraction = sliderState.value
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(activeFraction)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            colorScheme.primary.copy(alpha = 0.7f),
                                            colorScheme.primary
                                        )
                                    )
                                )
                        )
                    }
                }
            }
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = -48f }
                .padding(horizontal = 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Calculate time based on the fraction and current window
            val currentMin = lockedWindow?.first ?: minPosition
            val currentWidth = lockedWindow?.second ?: windowSize
            val displayTime = currentMin + (sliderFraction * currentWidth).toLong()

            // Display time elapsed in current session
            Text(
                text = formatTime(displayTime),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum" // Tabular numbers for stable width
                ),
                color = colorScheme.onSurfaceVariant
            )

            // Function: Offset from live
            val offsetFromLiveMs = (loadedPosition + 1000L) - displayTime // Add 1s to match user-facing live edge
            val isLive = offsetFromLiveMs < 4000

            val livePulseAlpha by rememberInfiniteTransition(label = "livePulse").animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse"
            )

            Surface(
                onClick = {
                    if (!isLive) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSeek(loadedPosition)
                    }
                },
                enabled = isInteractive,
                shape = RoundedCornerShape(8.dp),
                color = if (isLive && isInteractive) colorScheme.primary.copy(alpha = 0.12f) else colorScheme.surfaceVariant.copy(
                    alpha = 0.5f
                ),
                border = if (isLive) null else BorderStroke(
                    1.dp,
                    colorScheme.outline.copy(alpha = 0.1f)
                ),
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .graphicsLayer { alpha = if (isLive) 1f else livePulseAlpha }
                            .background(
                                if (isLive && isInteractive) Color.Red else colorScheme.onSurfaceVariant.copy(
                                    alpha = 0.5f
                                ), CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isLive && isInteractive) "LIVE" else if (isLive) "OFFLINE" else "-${
                            formatOffset(
                                offsetFromLiveMs
                            )
                        }",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp,
                            fontFeatureSettings = "tnum"
                        ),
                        color = if (isLive && isInteractive) colorScheme.primary else colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

private fun formatOffset(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60))
    return if (minutes > 0) "%d:%02d".format(minutes, seconds) else "%ds".format(seconds)
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

@Composable
fun PlaybackControlRow(
    playbackState: StreamStates,
    playButtonSize: Dp,
    isTinyCompact: Boolean,
    isCompact: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    colorScheme: ColorScheme
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val btnSize = if (isTinyCompact) 36.dp else if (isCompact) 40.dp else 48.dp
        val iconSize = if (isTinyCompact) 18.dp else if (isCompact) 20.dp else 24.dp

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(btnSize)) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_player_prev),
                    contentDescription = stringResource(R.string.player_previous),
                    modifier = Modifier.size(iconSize - 4.dp),
                    tint = colorScheme.onSurfaceVariant
                )
            }
        }

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            IconButton(onClick = onSeekBack, modifier = Modifier.size(btnSize)) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_player_seek_back_10),
                    contentDescription = "Seek Back",
                    modifier = Modifier.size(iconSize + 8.dp),
                    tint = colorScheme.onSurfaceVariant
                )
            }
        }

        Box(modifier = Modifier.weight(1.2f), contentAlignment = Alignment.Center) {
            AnimatedPlayPauseButton(
                playbackState = playbackState,
                onClick = onPlayPause,
                size = playButtonSize
            )
        }

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            IconButton(onClick = onSeekForward, modifier = Modifier.size(btnSize)) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_player_seek_forward_10),
                    contentDescription = "Seek Forward",
                    modifier = Modifier.size(iconSize + 8.dp),
                    tint = colorScheme.onSurfaceVariant
                )
            }
        }

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            IconButton(onClick = onNext, modifier = Modifier.size(btnSize)) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_player_next),
                    contentDescription = stringResource(R.string.player_next),
                    modifier = Modifier.size(iconSize - 4.dp),
                    tint = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ActionButtonsRow(
    onRefresh: () -> Unit,
    onSleepClick: () -> Unit,
    colorScheme: ColorScheme
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            ActionButton(
                iconRes = R.drawable.ic_player_refresh,
                label = stringResource(R.string.player_refresh),
                onClick = onRefresh,
                colorScheme = colorScheme
            )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            ActionButton(
                iconRes = R.drawable.ic_player_timer,
                label = stringResource(R.string.player_sleep),
                onClick = onSleepClick,
                colorScheme = colorScheme
            )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CastButton(
                        modifier = Modifier.size(20.dp),
                        color = colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = stringResource(R.string.player_cast),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ActionButton(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    colorScheme: ColorScheme
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = label,
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PlayerLogoSection(
    stationUiState: StationUiState,
    playbackState: StreamStates,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val containerSize = minOf(maxWidth, maxHeight)
        val logoSize = containerSize * 0.75f

        if (playbackState is StreamStates.PLAYING) {
            val waveRadius1 by infiniteTransition.animateFloat(
                initialValue = 0f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )

            val waveRadius2 by infiniteTransition.animateFloat(
                initialValue = 0f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3000, easing = LinearEasing, delayMillis = 1500),
                    repeatMode = RepeatMode.Restart
                )
            )

            val ringAlpha by infiniteTransition.animateFloat(
                initialValue = 0.4f, targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )

            Canvas(modifier = Modifier.size(containerSize)) {
                val radiusBase = size.minDimension / 2

                // Wave 1 Fill
                drawCircle(
                    color = colorScheme.primary,
                    radius = radiusBase * waveRadius1,
                    alpha = (1f - waveRadius1) * 0.06f
                )

                // Wave 2 Fill
                drawCircle(
                    color = colorScheme.primary,
                    radius = radiusBase * waveRadius2,
                    alpha = (1f - waveRadius2) * 0.06f
                )

                // Outer Ring
                drawCircle(
                    color = colorScheme.primary,
                    radius = radiusBase * waveRadius1,
                    alpha = ringAlpha,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }

        // Logo Surface
        Surface(
            shape = RoundedCornerShape(logoSize * 0.10f),
            color = colorScheme.primary.copy(alpha = 0.08f),
            modifier = Modifier
                .size(logoSize)
                .graphicsLayer {
                    val s = if (playbackState is StreamStates.PLAYING) scale else 1f
                    scaleX = s
                    scaleY = s
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Use bundled state for synchronized animation
                AnimatedContent(
                    targetState = stationUiState,
                    transitionSpec = {
                        val direction = targetState.swipeDirection

                        val springSpec = spring<IntOffset>(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )

                        if (direction < 0f) {
                            (slideInHorizontally(springSpec) { -it } + fadeIn()) togetherWith
                                    (slideOutHorizontally(springSpec) { it } + fadeOut())
                        } else if (direction > 0f) {
                            (slideInHorizontally(springSpec) { it } + fadeIn()) togetherWith
                                    (slideOutHorizontally(springSpec) { -it } + fadeOut())
                        } else {
                            fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                        }
                    },
                    label = "logoTransition"
                ) { uiState ->
                    val station = uiState.station ?: return@AnimatedContent
                    val logoRes = LogoMapper.getLogoById(station.id)
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(logoRes)
                            .error(R.drawable.ic_radio_default)
                            .fallback(R.drawable.ic_radio_default)
                            .build(),
                        contentDescription = "${station.stationName} logo",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(logoSize * 0.2f),
                        contentScale = ContentScale.Fit,
                        colorFilter = if (logoRes == 0 || logoRes == R.drawable.ic_radio_default) {
                            ColorFilter.tint(MaterialTheme.colorScheme.primary)
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
fun AnimatedMetadataWithMarquee(
    metadata: String,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    AnimatedContent(
        targetState = metadata,
        transitionSpec = {
            (slideInVertically(initialOffsetY = { it / 2 }) + fadeIn()) togetherWith
                    (slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut())
        },
        modifier = if (isVisible) modifier else modifier.graphicsLayer { alpha = 0f },
        label = "metadataTransition"
    ) { currentMetadata ->
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = currentMetadata,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        animationMode = MarqueeAnimationMode.Immediately,
                        initialDelayMillis = 1000,
                        velocity = 30.dp
                    )
                )
            }
        }
    }
}

@Composable
fun AnimatedPlayPauseButton(
    playbackState: StreamStates,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 80.dp
) {
    val colorScheme = MaterialTheme.colorScheme
    val isPlaying = playbackState is StreamStates.PLAYING
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Animate button color transition
    val buttonColor by animateColorAsState(
        targetValue = if (isPlaying) colorScheme.primary else colorScheme.primary.copy(alpha = 0.8f),
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "buttonColor"
    )

    // Animate button elevation
    val buttonElevation by animateDpAsState(
        targetValue = if (isPlaying) 20.dp else 16.dp,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "buttonElevation"
    )

    // Press scale effect
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "buttonScale"
    )

    Box(
        modifier = modifier
            .size(size)
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = buttonScale
                scaleY = buttonScale
            },
        contentAlignment = Alignment.Center
    ) {
        // Main button
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = buttonElevation,
                    shape = CircleShape,
                    ambientColor = colorScheme.primary.copy(alpha = 0.3f),
                    spotColor = colorScheme.primary.copy(alpha = 0.3f)
                )
                .clip(CircleShape)
                .background(buttonColor)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null, // We handle visual feedback via scale and color
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            // Crossfade between play and pause icons
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = {
                    if (targetState) {
                        // Play -> Pause
                        (scaleIn(
                            initialScale = 0.6f,
                            animationSpec = tween(400, easing = FastOutSlowInEasing)
                        ) + fadeIn(tween(300))) togetherWith
                                (scaleOut(
                                    targetScale = 0.6f,
                                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                                ) + fadeOut(tween(200)))
                    } else {
                        // Pause -> Play
                        (scaleIn(
                            initialScale = 0.6f,
                            animationSpec = tween(400, easing = FastOutSlowInEasing)
                        ) + fadeIn(tween(300))) togetherWith
                                (scaleOut(
                                    targetScale = 0.6f,
                                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                                ) + fadeOut(tween(200)))
                    }
                },
                label = "playPauseIcon"
            ) { playing ->
                Icon(
                    painter = painterResource(id = if (playing) R.drawable.ic_player_pause else R.drawable.ic_player_play),
                    contentDescription = if (playing) stringResource(R.string.player_pause) else stringResource(
                        R.string.player_play
                    ),
                    modifier = Modifier.size(size * 0.5f),
                    tint = Color.White
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerBandSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    colorScheme: ColorScheme
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    val thumbSize by animateDpAsState(
        targetValue = if (isPressed) 16.dp else 12.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "thumbSize"
    )

    val trackHeight by animateDpAsState(
        targetValue = if (isPressed) 8.dp else 4.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "trackHeight"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text(
                    "${value.toInt()} dB",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary
                )
            }

            Slider(
                value = value,
                onValueChange = {
                    if (it != value) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    onValueChange(it)
                },
                onValueChangeFinished = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onValueChangeFinished()
                },
                valueRange = -15f..15f,
                interactionSource = interactionSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                thumb = {
                    Box(
                        modifier = Modifier.size(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            shadowElevation = if (isPressed) 3.dp else 1.dp,
                            modifier = Modifier.size(thumbSize)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(2.dp)
                                    .background(colorScheme.primary, CircleShape)
                            )
                        }
                    }
                },
                track = { sliderState ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(trackHeight)
                            .clip(CircleShape)
                            .background(colorScheme.onSurface.copy(alpha = 0.1f))
                    ) {
                        // Progress Track with Gradient (Centered at 0dB)
                        // For EQ, usually we show from the center or from the left. 
                        // To match AudioSeekBar behavior, we show from left to current value.
                        val fraction = (sliderState.value + 15f) / 30f
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            colorScheme.primary.copy(alpha = 0.7f),
                                            colorScheme.primary
                                        )
                                    )
                                )
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun EqualizerDialog(
    currentLevels: Map<Int, Short>,
    onDismiss: () -> Unit,
    onBandChange: (Int, Short) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val bands = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = colorScheme.surface,
        title = {
            Text(
                text = stringResource(R.string.player_equalizer),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                bands.forEachIndexed { index, frequency ->
                    var localLevel by remember(currentLevels[index]) {
                        mutableFloatStateOf((currentLevels[index] ?: 0).toFloat() / 100f)
                    }

                    EqualizerBandSlider(
                        label = frequency,
                        value = localLevel,
                        onValueChange = { localLevel = it },
                        onValueChangeFinished = {
                            onBandChange(index, (localLevel * 100).toInt().toShort())
                        },
                        colorScheme = colorScheme
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.player_done))
            }
        },
        dismissButton = {
            TextButton(onClick = { (0..4).forEach { onBandChange(it, 0) } }) {
                Text(stringResource(R.string.player_reset), color = colorScheme.error)
            }
        }
    )
}

@Composable
fun SleepTimerDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val options = listOf(5, 10, 15, 30, 45, 60)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = colorScheme.surface,
        titleContentColor = colorScheme.onSurface,
        textContentColor = colorScheme.onSurfaceVariant,
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.ic_player_timer),
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                stringResource(R.string.player_sleep_timer),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    stringResource(R.string.player_stop_playback_after),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                options.forEach { minutes ->
                    Surface(
                        onClick = { onConfirm(minutes) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (minutes < 60) stringResource(
                                    R.string.player_minutes,
                                    minutes
                                )
                                else stringResource(R.string.player_hour),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                painter = painterResource(id = R.drawable.ic_player_chevron_right),
                                contentDescription = null,
                                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (minutes != options.last()) {
                        HorizontalDivider(
                            color = colorScheme.outline.copy(alpha = 0.2f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.primary)
            ) {
                Text(stringResource(R.string.player_cancel), fontWeight = FontWeight.Medium)
            }
        }
    )
}

@Composable
fun EmptyPlayerContent(
    modifier: Modifier = Modifier,
    colorScheme: ColorScheme
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(id = R.drawable.ic_player_note),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.player_no_station_playing),
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.player_select_station_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
