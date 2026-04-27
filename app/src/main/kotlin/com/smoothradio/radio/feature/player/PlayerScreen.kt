package com.smoothradio.radio.feature.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.smoothradio.radio.R
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.ui.DotLoadingAnimation
import com.smoothradio.radio.core.ui.PlayerControlViewModel
import com.smoothradio.radio.core.ui.RadioViewModel
import com.smoothradio.radio.core.ui.SimpleTopBar
import com.smoothradio.radio.service.StreamService
import kotlinx.coroutines.delay
import kotlin.collections.emptyList

@Composable
fun PlayerScreen(
    playerControlViewModel: PlayerControlViewModel,
    modifier: Modifier = Modifier
) {
    val playingStation by playerControlViewModel.playingStation.collectAsStateWithLifecycle()
    val playbackState by playerControlViewModel.playbackState.collectAsStateWithLifecycle()
    val metadata by playerControlViewModel.metadata.collectAsStateWithLifecycle()
    val colorScheme = MaterialTheme.colorScheme

    // Return empty state if no station
    if (playingStation == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No station playing",
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Select a station to start listening",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        return
    }

    val currentStation = playingStation!!

    // Request state update when screen becomes visible
    LaunchedEffect(Unit) {
        playerControlViewModel.requestStateUpdate()
    }

    val animatedColor by animateColorAsState(
        targetValue = when {
            playbackState == "Playing" -> colorScheme.primary.copy(alpha = 0.15f)
            playbackState == "Buffering" || playbackState == "Preparing Audio" -> colorScheme.tertiary.copy(alpha = 0.15f)
            else -> colorScheme.surfaceVariant
        },
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "background color"
    )

    Column(modifier = modifier.fillMaxSize()) {
        SimpleTopBar(title = "LIVE")

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            animatedColor,
                            colorScheme.background
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Top
            ) {
                val infiniteTransition = rememberInfiniteTransition()
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.03f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )

                // Logo
                Box(
                    modifier = Modifier.size(240.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Radar wave rings
                    if (playbackState == "Playing") {
                        val waveRadius1 by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(3000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            )
                        )

                        val waveRadius2 by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(3000, easing = LinearEasing, delayMillis = 1500),
                                repeatMode = RepeatMode.Restart
                            )
                        )

                        Box(
                            modifier = Modifier
                                .size(240.dp * waveRadius1)
                                .align(Alignment.Center)
                                .clip(CircleShape)
                                .background(
                                    colorScheme.primary.copy(
                                        alpha = (1f - waveRadius1) * 0.06f
                                    )
                                )
                        )

                        Box(
                            modifier = Modifier
                                .size(240.dp * waveRadius2)
                                .align(Alignment.Center)
                                .clip(CircleShape)
                                .background(
                                    colorScheme.primary.copy(
                                        alpha = (1f - waveRadius2) * 0.06f
                                    )
                                )
                        )

                        val ringAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(3000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            )
                        )

                        Box(
                            modifier = Modifier
                                .size(240.dp * waveRadius1)
                                .align(Alignment.Center)
                                .clip(CircleShape)
                                .border(
                                    width = 1.dp,
                                    color = colorScheme.primary.copy(alpha = ringAlpha),
                                    shape = CircleShape
                                )
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(40.dp),
                        color = colorScheme.primary.copy(alpha = 0.08f),
                        modifier = Modifier
                            .size(180.dp)
                            .scale(if (playbackState == "Playing") scale else 1f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(id = currentStation.logoResource),
                                contentDescription = "${currentStation.stationName} logo",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(36.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = currentStation.stationName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Status indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    when {
                        playbackState == "Playing" -> {
                            Text(
                                text = "NOW PLAYING",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                letterSpacing = 1.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.primary
                            )
                        }
                        playbackState == "Buffering" || playbackState == "Preparing Audio" -> {
                            DotLoadingAnimation(
                                dotSize = 8.dp,
                                dotSpacing = 6.dp,
                                color = colorScheme.tertiary,
                                animationDelay = 200,
                                animationDuration = 400
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "BUFFERING",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                letterSpacing = 1.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.tertiary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Animated Metadata with marquee
                if (playbackState == "Playing") {
                    AnimatedMetadataWithMarquee(
                        metadata = metadata,
                        isVisible = playbackState == "Playing"
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Playback Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            modifier = Modifier.size(32.dp),
                            tint = colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedPlayPauseButton(
                        playbackState = playbackState,
                        onClick = { playerControlViewModel.requestPlayStation(currentStation) }
                    )

                    IconButton(
                        onClick = { },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            modifier = Modifier.size(32.dp),
                            tint = colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Secondary Controls
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
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                modifier = Modifier.size(24.dp),
                                tint = colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Refresh",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = { },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = "Sleep Timer",
                                modifier = Modifier.size(24.dp),
                                tint = colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Sleep",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = { },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                modifier = Modifier.size(24.dp),
                                tint = colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Share",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Ad Banner
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
                                    adUnitId = "ca-app-pub-9799428944156340/4540584810"
                                    setAdSize(AdSize.BANNER)
                                    loadAd(AdRequest.Builder().build())
                                }
                            },
                            modifier = Modifier
                                .wrapContentWidth()
                                .fillMaxHeight()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
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
    val isPlaying = playbackState == "Playing"

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

    Box(
        modifier = modifier.size(80.dp),
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
                .clickable { onClick() },
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
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    modifier = Modifier
                        .size(48.dp),
                    tint = Color.White
                )
            }
        }
    }
}