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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.smoothradio.radio.R
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.domain.model.StreamStates
import com.smoothradio.radio.core.ui.PlayerControlViewModel
import com.smoothradio.radio.core.ui.common.AdBanner
import com.smoothradio.radio.core.ui.common.DotLoadingAnimation
import com.smoothradio.radio.core.ui.common.SimpleTopBar
import com.smoothradio.radio.core.ui.common.rememberSafeLogoId
import com.smoothradio.radio.core.util.AdConfig
import timber.log.Timber

@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    playerControlViewModel: PlayerControlViewModel = hiltViewModel()
) {
    val playingStation by playerControlViewModel.playingStation.collectAsStateWithLifecycle()
    val playbackState by playerControlViewModel.playbackState.collectAsStateWithLifecycle()
    val metadata by playerControlViewModel.metadata.collectAsStateWithLifecycle()
    val colorScheme = MaterialTheme.colorScheme

    var swipeDirection by remember { mutableFloatStateOf(0f) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var showEqDialog by remember { mutableStateOf(false) }

    if (playingStation == null) {
        EmptyPlayerContent(modifier = modifier, colorScheme = colorScheme)
        return
    }

    val currentStation = playingStation!!

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

        LaunchedEffect(screenWidth, screenHeight) {
            Timber.d("PlayerScreen Size: Width=$screenWidth, Height=$screenHeight, Landscape=$isLandscape")
            Timber.d("Current Screen Height: $screenHeight")
        }

        // Responsive Layout Configuration
        val layoutConfig = remember(screenHeight, screenWidth) {
            val isTinyCompact = if (isLandscape) screenHeight < 260.dp else screenHeight < 200.dp
            val isCompact = screenHeight in 200.dp..380.dp
            val isShrinking = screenHeight in 380.dp..425.dp
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

            val playButtonScale = when {
                isTinyCompact -> 0.75f
                isShrinking -> {
                    val range = 425f - 300f
                    val position = screenHeight.value - 300f
                    0.65f + (position / range) * 0.35f
                }

                else -> 1f
            }

            object {
                val showAd = if (isLandscape) screenHeight > 500.dp else screenHeight > 670.dp
                val showSecondRow = if (isLandscape) screenHeight >= 440.dp else screenHeight > 740.dp
                val showMetadata = if (isLandscape) screenHeight > 320.dp else screenHeight > 640.dp
                val logoAlpha = logoVisibility
                val tinyCompact = isTinyCompact
                val compact = isCompact
                val shrinking = isShrinking
                val btnScale = playButtonScale
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
                    else -> 16.dp
                }

                val logoScale = when {
                    isLandscape -> 0.45f
                    isWidePortrait -> 0.5f // Smaller scale for wide portrait to prevent crowding
                    isShrinking -> 0.4f
                    isMedium -> 0.55f
                    else -> 0.7f
                } * logoVisibility
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
                                    currentStation = currentStation,
                                    playbackState = playbackState,
                                    swipeDirection = swipeDirection,
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
                                Spacer(modifier = Modifier.height(24.dp))
                            }

                            PlaybackControlRow(
                                playbackState = playbackState,
                                playButtonScale = layoutConfig.btnScale,
                                isTinyCompact = layoutConfig.tinyCompact,
                                isCompact = layoutConfig.compact,
                                onPrevious = {
                                    swipeDirection = -1f; playerControlViewModel.requestPreviousStation()
                                },
                                onNext = {
                                    swipeDirection = 1f; playerControlViewModel.requestNextStation()
                                },
                                onPlayPause = { playerControlViewModel.requestPlayStation(currentStation) },
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
                            .padding(top = layoutConfig.topPadding, bottom = 8.dp), // Reduced bottom padding
                        verticalArrangement = if (layoutConfig.tinyCompact || layoutConfig.compact) Arrangement.Center else Arrangement.Top
                    ) {
                        // Logo Section
                        if (layoutConfig.logoAlpha > 0f) {
                            PlayerLogoSection(
                                currentStation = currentStation,
                                playbackState = playbackState,
                                swipeDirection = swipeDirection,
                                modifier = Modifier
                                    .fillMaxWidth(layoutConfig.logoScale)
                                    .fillMaxHeight(0.40f) // Slightly reduced max height
                                    .sizeIn(maxWidth = 400.dp, maxHeight = 400.dp)
                                    .aspectRatio(1f)
                            )
                            Spacer(
                                modifier = Modifier.height(
                                    (if (layoutConfig.shrinking) 4.dp else 12.dp) * layoutConfig.logoAlpha
                                )
                            )
                        }

                        // Station Name
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
                                    if (layoutConfig.showAd) 12.dp else 8.dp
                                )
                            )
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

                        if (!layoutConfig.tinyCompact) {
                            Spacer(
                                modifier = Modifier.height(
                                    if (layoutConfig.compact || layoutConfig.shrinking) 8.dp else 12.dp
                                )
                            )
                        }
                        if (!layoutConfig.compact && !layoutConfig.tinyCompact) Spacer(
                            modifier = Modifier.weight(
                                1f
                            )
                        )

                        // Playback Controls
                        PlaybackControlRow(
                            playbackState = playbackState,
                            playButtonScale = layoutConfig.btnScale,
                            isTinyCompact = layoutConfig.tinyCompact,
                            isCompact = layoutConfig.compact,
                            onPrevious = {
                                swipeDirection = -1f; playerControlViewModel.requestPreviousStation()
                            },
                            onNext = {
                                swipeDirection = 1f; playerControlViewModel.requestNextStation()
                            },
                            onPlayPause = {
                                playerControlViewModel.requestPlayStation(
                                    currentStation
                                )
                            },
                            colorScheme = colorScheme
                        )

                        // Secondary controls
                        if (layoutConfig.showSecondRow) {
                            Spacer(modifier = Modifier.height(if (layoutConfig.showAd) 16.dp else 24.dp))
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

        Spacer(modifier = Modifier.height(8.dp))
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

@Composable
fun PlaybackControlRow(
    playbackState: StreamStates,
    playButtonScale: Float,
    isTinyCompact: Boolean,
    isCompact: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
    colorScheme: ColorScheme
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = playButtonScale; scaleY = playButtonScale },
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val btnSize = if (isTinyCompact) 36.dp else if (isCompact) 40.dp else 56.dp
        val iconSize = if (isTinyCompact) 18.dp else if (isCompact) 20.dp else 28.dp

        IconButton(onClick = onPrevious, modifier = Modifier.size(btnSize)) {
            Icon(
                painter = painterResource(id = R.drawable.ic_player_prev),
                contentDescription = stringResource(R.string.player_previous),
                modifier = Modifier.size(iconSize),
                tint = colorScheme.onSurfaceVariant
            )
        }
        AnimatedPlayPauseButton(
            playbackState = playbackState,
            onClick = onPlayPause,
            modifier = if (isTinyCompact) Modifier.size(56.dp) else if (isCompact) Modifier.size(64.dp) else Modifier
        )
        IconButton(onClick = onNext, modifier = Modifier.size(btnSize)) {
            Icon(
                painter = painterResource(id = R.drawable.ic_player_next),
                contentDescription = stringResource(R.string.player_next),
                modifier = Modifier.size(iconSize),
                tint = colorScheme.onSurfaceVariant
            )
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
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionButton(
            iconRes = R.drawable.ic_player_refresh,
            label = stringResource(R.string.player_refresh),
            onClick = onRefresh,
            colorScheme = colorScheme
        )
        ActionButton(
            iconRes = R.drawable.ic_player_timer,
            label = stringResource(R.string.player_sleep),
            onClick = onSleepClick,
            colorScheme = colorScheme
        )
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
    currentStation: RadioStation,
    playbackState: StreamStates,
    swipeDirection: Float,
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
                // Use a Pair of ID and logo as targetState to ensure the transition captures the specific logo
                // and doesn't update the outgoing content's logo when currentStation changes.
                AnimatedContent(
                    targetState = currentStation.id to currentStation.logoResource,
                    transitionSpec = {
                        val springSpec = spring<IntOffset>(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                        if (swipeDirection < 0f) {
                            (slideInHorizontally(springSpec) { -it } + fadeIn()) togetherWith
                                    (slideOutHorizontally(springSpec) { it } + fadeOut())
                        } else if (swipeDirection > 0f) {
                            (slideInHorizontally(springSpec) { it } + fadeIn()) togetherWith
                                    (slideOutHorizontally(springSpec) { -it } + fadeOut())
                        } else {
                            fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                        }
                    },
                    label = "logoTransition"
                ) { (stationId, logoRes) ->
                    val safeLogoId = rememberSafeLogoId(logoRes)
                    Image(
                        painter = painterResource(id = safeLogoId),
                        contentDescription = "Station logo",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(logoSize * 0.2f),
                        contentScale = ContentScale.Fit,
                        colorFilter = if (safeLogoId == R.drawable.ic_radio_default) {
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
    modifier: Modifier = Modifier
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
            .size(80.dp)
            .graphicsLayer {
                scaleX = buttonScale
                scaleY = buttonScale
            },
        contentAlignment = Alignment.Center
    ) {
        // Main button
        Box(
            modifier = Modifier
                .size(80.dp)
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
                    modifier = Modifier.size(40.dp),
                    tint = Color.White
                )
            }
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
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                bands.forEachIndexed { index, frequency ->
                    var localLevel by remember(currentLevels[index]) {
                        mutableFloatStateOf((currentLevels[index] ?: 0).toFloat() / 100f)
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(frequency, style = MaterialTheme.typography.labelLarge)
                                Text(
                                    "${localLevel.toInt()} dB",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = localLevel,
                                onValueChange = { localLevel = it },
                                onValueChangeFinished = {
                                    onBandChange(index, (localLevel * 100).toInt().toShort())
                                },
                                valueRange = -15f..15f,
                                modifier = Modifier.height(20.dp)
                            )
                        }
                    }
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
