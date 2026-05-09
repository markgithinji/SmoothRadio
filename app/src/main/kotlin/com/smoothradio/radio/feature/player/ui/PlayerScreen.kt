package com.smoothradio.radio.feature.player.ui

import android.util.Log
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.smoothradio.radio.R
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.ui.PlayerControlViewModel
import com.smoothradio.radio.core.ui.common.DotLoadingAnimation
import com.smoothradio.radio.core.ui.common.SimpleTopBar
import com.smoothradio.radio.service.StreamService

@Composable
fun PlayerScreen(
    playerControlViewModel: PlayerControlViewModel,
    modifier: Modifier = Modifier
) {
    val playingStation by playerControlViewModel.playingStation.collectAsStateWithLifecycle()
    val playbackState by playerControlViewModel.playbackState.collectAsStateWithLifecycle()
    val metadata by playerControlViewModel.metadata.collectAsStateWithLifecycle()
    val colorScheme = MaterialTheme.colorScheme

    var swipeDirection by remember { mutableFloatStateOf(0f) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var showEqDialog by remember { mutableStateOf(false) }

    if (playingStation == null) {
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
                    "No station playing",
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurfaceVariant
                )
                Text(
                    "Select a station to start listening",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        return
    }

    val currentStation = playingStation!!

    val animatedColor by animateColorAsState(
        targetValue = when {
            playbackState == StreamService.StreamStates.PLAYING -> colorScheme.primary.copy(alpha = 0.15f)
            playbackState == StreamService.StreamStates.BUFFERING || playbackState == StreamService.StreamStates.PREPARING -> colorScheme.tertiary.copy(
                alpha = 0.15f
            )

            else -> colorScheme.surfaceVariant
        },
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "background color"
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        val showAd = screenHeight > 670.dp
        val showSecondRow = screenHeight > 626.dp
        val showMetadata = screenHeight > 480.dp
        val isTinyCompact = screenHeight < 200.dp
        val isCompact = screenHeight in 200.dp..380.dp
        val isShrinking = screenHeight in 380.dp..425.dp
        val isMedium = screenHeight in 426.dp..550.dp

        LaunchedEffect(maxHeight) {
            Log.d("PlayerScreen", "Height: $screenHeight")
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

        Column(modifier = Modifier.fillMaxSize()) {
            SimpleTopBar(
                title = "LIVE",
                actionIcon = {
                    IconButton(onClick = { showEqDialog = true }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_toolbar_eq),
                            contentDescription = "Equalizer",
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
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                animatedColor,
                                colorScheme.background
                            ), startY = 0f, endY = Float.POSITIVE_INFINITY
                        )
                    )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = when {
                                isTinyCompact -> 8.dp; isCompact -> 8.dp; isShrinking -> 12.dp; isMedium -> 16.dp; else -> 24.dp
                            },
                            end = when {
                                isTinyCompact -> 8.dp; isCompact -> 8.dp; isShrinking -> 12.dp; isMedium -> 16.dp; else -> 24.dp
                            },
                            top = when {
                                isTinyCompact -> 4.dp; isCompact -> 4.dp; isShrinking -> 6.dp; isMedium -> 8.dp; else -> 16.dp
                            },
                            bottom = when {
                                isTinyCompact -> 4.dp; isCompact -> 8.dp; isShrinking -> 10.dp; isMedium -> 12.dp; else -> 16.dp
                            }
                        ),
                    verticalArrangement = when {
                        isTinyCompact -> Arrangement.Center
                        isCompact -> Arrangement.Center
                        else -> Arrangement.Top
                    }
                ) {
                    // Logo
                    if (!isCompact && !isTinyCompact) {
                        PlayerLogoSection(
                            currentStation = currentStation,
                            playbackState = playbackState,
                            swipeDirection = swipeDirection,
                            modifier = Modifier
                                .fillMaxWidth(
                                    when {
                                        isShrinking -> 0.4f
                                        isMedium -> 0.55f
                                        else -> 0.7f
                                    }
                                )
                                .aspectRatio(1f)
                        )
                        Spacer(
                            modifier = Modifier.height(
                                when {
                                    isShrinking -> 8.dp
                                    isMedium -> 12.dp
                                    else -> 16.dp
                                }
                            )
                        )
                    }

                    // Station Name
                    if (!isTinyCompact) {
                        Text(
                            text = currentStation.stationName,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = when {
                                isCompact -> 16.sp
                                isShrinking -> 18.sp
                                isMedium -> 20.sp
                                else -> 24.sp
                            },
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Reserved space for playing state to prevent layout shifts
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier.height(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                when {
                                    playbackState == StreamService.StreamStates.PLAYING -> Text(
                                        "NOW PLAYING",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 11.sp,
                                        letterSpacing = 1.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colorScheme.primary
                                    )

                                    playbackState == StreamService.StreamStates.BUFFERING || playbackState == StreamService.StreamStates.PREPARING -> {
                                        DotLoadingAnimation(
                                            dotSize = if (isCompact || isShrinking) 6.dp else 8.dp,
                                            dotSpacing = if (isCompact || isShrinking) 4.dp else 6.dp,
                                            color = colorScheme.tertiary,
                                            animationDelay = 200,
                                            animationDuration = 400
                                        )
                                        Spacer(modifier = Modifier.width(if (isCompact || isShrinking) 6.dp else 8.dp))
                                        Text(
                                            "BUFFERING",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = if (isCompact || isShrinking) 9.sp else 10.sp,
                                            letterSpacing = 1.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = colorScheme.tertiary
                                        )
                                    }
                                    // Idle state: empty but space is reserved
                                }
                            }
                        }
                    }

                    // Metadata
                    if (playbackState == StreamService.StreamStates.PLAYING && showMetadata && !isTinyCompact) {
                        Spacer(modifier = Modifier.height(if (showAd) 16.dp else 10.dp))
                        if (showAd) {
                            AnimatedMetadataWithMarquee(metadata = metadata, isVisible = true)
                        } else {
                            AnimatedMetadataWithMarquee(
                                metadata = metadata,
                                isVisible = true,
                                modifier = Modifier.height(36.dp)
                            )
                        }
                    }

                    // Spacer between content and controls
                    if (!isTinyCompact) {
                        Spacer(modifier = Modifier.height(if (isCompact || isShrinking) 12.dp else 16.dp))
                    }
                    if (!isCompact && !isTinyCompact) Spacer(modifier = Modifier.weight(1f))

                    // Playback Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { scaleX = playButtonScale; scaleY = playButtonScale },
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val btnSize = when {
                            isTinyCompact -> 36.dp; isCompact -> 40.dp; else -> 56.dp
                        }
                        val iconSize = when {
                            isTinyCompact -> 18.dp; isCompact -> 20.dp; else -> 28.dp
                        }

                        IconButton(onClick = {
                            swipeDirection = -1f; playerControlViewModel.requestPreviousStation()
                        }, modifier = Modifier.size(btnSize)) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_player_prev),
                                contentDescription = "Previous",
                                modifier = Modifier.size(iconSize),
                                tint = colorScheme.onSurfaceVariant
                            )
                        }
                        AnimatedPlayPauseButton(
                            playbackState = playbackState,
                            onClick = { playerControlViewModel.requestPlayStation(currentStation) },
                            modifier = if (isTinyCompact) Modifier.size(56.dp) else if (isCompact) Modifier.size(
                                64.dp
                            ) else Modifier
                        )
                        IconButton(onClick = {
                            swipeDirection = 1f; playerControlViewModel.requestNextStation()
                        }, modifier = Modifier.size(btnSize)) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_player_next),
                                contentDescription = "Next",
                                modifier = Modifier.size(iconSize),
                                tint = colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Secondary controls
                    if (showSecondRow) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = { playerControlViewModel.requestRefresh() },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_player_refresh),
                                        contentDescription = "Refresh",
                                        modifier = Modifier.size(22.dp),
                                        tint = colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    "Refresh",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = { showSleepDialog = true },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_player_timer),
                                        contentDescription = "Sleep Timer",
                                        modifier = Modifier.size(24.dp),
                                        tint = colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    "Sleep",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
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
                                    "Cast",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Ad
                    if (showAd) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Card(
                                modifier = Modifier
                                    .wrapContentWidth()
                                    .height(70.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        AdView(ctx).apply {
                                            adUnitId =
                                                "ca-app-pub-9799428944156340/4540584810"; setAdSize(
                                            AdSize.BANNER
                                        ); loadAd(
                                            AdRequest.Builder().build()
                                        )
                                        }
                                    },
                                    modifier = Modifier
                                        .wrapContentWidth()
                                        .fillMaxHeight()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

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
                showSleepDialog = false; playerControlViewModel.setSleepTimer(
                minutes
            )
            }
        )
    }
}

@Composable
fun PlayerLogoSection(
    currentStation: RadioStation,
    playbackState: String,
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
        // Calculate proportional sizes based on available space
        val containerSize = minOf(maxWidth, maxHeight)
        val logoSize = containerSize * 0.75f  // Logo is 75% of container
        val ringBaseSize = containerSize       // Rings fill the container

        Box(
            modifier = Modifier.size(containerSize),
            contentAlignment = Alignment.Center
        ) {
            // Radar wave rings
            if (playbackState == StreamService.StreamStates.PLAYING) {
                val waveRadius1 by infiniteTransition.animateFloat(
                    initialValue = 0f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            3000,
                            easing = LinearEasing
                        ), repeatMode = RepeatMode.Restart
                    )
                )

                val waveRadius2 by infiniteTransition.animateFloat(
                    initialValue = 0f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            3000,
                            easing = LinearEasing,
                            delayMillis = 1500
                        ), repeatMode = RepeatMode.Restart
                    )
                )

                Box(
                    modifier = Modifier
                        .size(ringBaseSize * waveRadius1)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(colorScheme.primary.copy(alpha = (1f - waveRadius1) * 0.06f))
                )

                Box(
                    modifier = Modifier
                        .size(ringBaseSize * waveRadius2)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(colorScheme.primary.copy(alpha = (1f - waveRadius2) * 0.06f))
                )

                val ringAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f, targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            3000,
                            easing = LinearEasing
                        ), repeatMode = RepeatMode.Restart
                    )
                )

                Box(
                    modifier = Modifier
                        .size(ringBaseSize * waveRadius1)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .border(1.dp, colorScheme.primary.copy(alpha = ringAlpha), CircleShape)
                )
            }

            // Logo Surface
            Surface(
                shape = RoundedCornerShape(logoSize * 0.10f),
                color = colorScheme.primary.copy(alpha = 0.08f),
                modifier = Modifier
                    .size(logoSize)
                    .scale(if (playbackState == StreamService.StreamStates.PLAYING) scale else 1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = currentStation.id,
                        transitionSpec = {
                            if (swipeDirection < 0f) {
                                (slideInHorizontally(
                                    initialOffsetX = { -it },
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                ) + fadeIn(tween(200))) togetherWith
                                        (slideOutHorizontally(
                                            targetOffsetX = { it },
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        ) + fadeOut(tween(200)))
                            } else if (swipeDirection > 0f) {
                                (slideInHorizontally(
                                    initialOffsetX = { it },
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                ) + fadeIn(tween(200))) togetherWith
                                        (slideOutHorizontally(
                                            targetOffsetX = { -it },
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        ) + fadeOut(tween(200)))
                            } else {
                                fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                            }
                        },
                        label = "logoTransition"
                    ) { _ ->
                        Image(
                            painter = painterResource(id = currentStation.logoResource),
                            contentDescription = "${currentStation.stationName} logo",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(logoSize * 0.2f),
                            contentScale = ContentScale.Fit
                        )
                    }
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

    // Fade and slide animation for metadata appearance
    val metadataVisible by animateFloatAsState(
        targetValue = if (isVisible && metadata.isNotEmpty()) 1f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "metadataVisibility"
    )

    val metadataOffset by animateFloatAsState(
        targetValue = if (isVisible && metadata.isNotEmpty()) 0f else 20f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "metadataOffset"
    )

    AnimatedContent(
        targetState = metadata,
        transitionSpec = {
            (slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = tween(500, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(400))) togetherWith
                    (slideOutVertically(
                        targetOffsetY = { -it / 2 },
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(300)))
        },
        label = "metadataTransition"
    ) { currentMetadata ->
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp)
                .graphicsLayer {
                    alpha = metadataVisible
                    translationY = metadataOffset
                },
            contentAlignment = Alignment.Center
        ) {
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
}

@Composable
fun AnimatedPlayPauseButton(
    playbackState: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val isPlaying = playbackState == StreamService.StreamStates.PLAYING
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
                    contentDescription = if (playing) "Pause" else "Play",
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
        tonalElevation = 6.dp,
        title = {
            Text(
                "Equalizer",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                bands.forEachIndexed { index, frequency ->
                    var localLevel by remember(currentLevels[index]) {
                        mutableStateOf((currentLevels[index] ?: 0).toFloat() / 100f)
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (localLevel != 0f)
                            colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else
                            colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    frequency,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Normal,
                                    color = colorScheme.onSurface
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (localLevel != 0f)
                                        colorScheme.primary
                                    else
                                        colorScheme.outlineVariant
                                ) {
                                    Text(
                                        "${if (localLevel > 0) "+" else ""}${(localLevel).toInt()} dB",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (localLevel != 0f)
                                            colorScheme.onPrimary
                                        else
                                            colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(
                                            horizontal = 8.dp,
                                            vertical = 2.dp
                                        )
                                    )
                                }
                            }
                            Slider(
                                value = localLevel,
                                onValueChange = { localLevel = it },
                                onValueChangeFinished = {
                                    onBandChange(index, (localLevel * 100).toInt().toShort())
                                },
                                valueRange = -15f..15f,
                                modifier = Modifier.height(20.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = colorScheme.primary,
                                    activeTrackColor = colorScheme.primary,
                                    inactiveTrackColor = colorScheme.outlineVariant
                                )
                            )
                            // dB indicators
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "-15",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant,
                                    fontSize = 9.sp
                                )
                                Text(
                                    "0",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant,
                                    fontSize = 9.sp
                                )
                                Text(
                                    "+15",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
            ) {
                Text(
                    "Done",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    for (i in 0 until 5) {
                        onBandChange(i, 0)
                    }
                }
            ) {
                Text("Reset", color = colorScheme.error, fontWeight = FontWeight.Medium)
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
                "Sleep Timer",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Stop playback after:",
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
                                text = if (minutes < 60) "$minutes minutes" else "1 hour",
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
                Text("Cancel", fontWeight = FontWeight.Medium)
            }
        }
    )
}