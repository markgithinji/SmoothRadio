package com.smoothradio.radio.feature.radiolist.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.smoothradio.radio.R
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.domain.model.StreamStates
import com.smoothradio.radio.core.ui.util.LogoMapper
import com.smoothradio.radio.core.ui.common.DotLoadingAnimation
import com.smoothradio.radio.core.ui.common.MiniWaveformVisualization
import com.smoothradio.radio.core.ui.common.pulseAnimation

@Composable
fun PersistentMiniPlayer(
    station: RadioStation?,
    playbackState: StreamStates,
    onPlayPauseClick: () -> Unit
) {
    // Don't render if no station
    if (station == null) return

    val isBuffering =
        playbackState is StreamStates.BUFFERING || playbackState is StreamStates.PREPARING
    val isPlaying = playbackState is StreamStates.PLAYING
    val colorScheme = MaterialTheme.colorScheme
    val outlineVariantColor = colorScheme.outlineVariant.copy(alpha = 0.2f)

    val overlayColor by animateColorAsState(
        targetValue = when {
            isPlaying -> colorScheme.primary.copy(alpha = 0.08f)
            isBuffering -> colorScheme.tertiary.copy(alpha = 0.08f)
            else -> Color.Transparent
        },
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "overlayColor"
    )

    Box(
        modifier = Modifier
            .testTag("persistent_mini_player")
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 0.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(colorScheme.surfaceVariant.copy(alpha = 0.95f))
            .background(overlayColor)
            .drawBehind {
                val strokeWidth = 1.5.dp.toPx()
                val y = size.height - strokeWidth / 2
                drawLine(
                    color = outlineVariantColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = strokeWidth
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .border(0.5.dp, colorScheme.outline.copy(alpha = 0.6f))
                    .pulseAnimation(enabled = isBuffering, targetValue = 1.05f),
                contentAlignment = Alignment.Center
            ) {
                val logoRes = LogoMapper.getLogoById(station.id)
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(logoRes)
                        .error(R.drawable.ic_radio_default)
                        .fallback(R.drawable.ic_radio_default)
                        .build(),
                    contentDescription = stringResource(
                        R.string.station_logo_content_description,
                        station.stationName
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp),
                    contentScale = ContentScale.Fit,
                    colorFilter = if (logoRes == 0 || logoRes == R.drawable.ic_radio_default) {
                        ColorFilter.tint(colorScheme.primary)
                    } else null
                )

                if (isBuffering) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colorScheme.primary.copy(alpha = 0.15f))
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station.stationName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val statusColor by animateColorAsState(
                    targetValue = when {
                        isPlaying -> colorScheme.primary
                        isBuffering -> colorScheme.tertiary
                        else -> colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(500, easing = FastOutSlowInEasing),
                    label = "statusColor"
                )

                Box(modifier = Modifier.height(14.dp)) {
                    when {
                        isBuffering -> {
                            Text(
                                text = stringResource(R.string.player_buffering),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = statusColor,
                                letterSpacing = 0.5.sp
                            )
                        }

                        isPlaying -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.player_playing).uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = statusColor,
                                    letterSpacing = 0.5.sp
                                )
                                MiniWaveformVisualization(
                                    barCount = 6,
                                    barWidth = 2.dp,
                                    barSpacing = 1.5.dp,
                                    height = 10.dp,
                                    color = statusColor
                                )
                            }
                        }

                        else -> {
                            Text(
                                text = station.location,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            MiniPlayerControl(
                playbackState = playbackState,
                onPlayPauseClick = onPlayPauseClick,
                colorScheme = colorScheme
            )
        }
    }
}

@Composable
fun MiniPlayerControl(
    playbackState: StreamStates,
    onPlayPauseClick: () -> Unit,
    colorScheme: ColorScheme
) {
    val isBuffering =
        playbackState is StreamStates.BUFFERING || playbackState is StreamStates.PREPARING
    val isPlaying = playbackState is StreamStates.PLAYING

    AnimatedContent(
        targetState = when {
            isBuffering -> "buffering"
            isPlaying -> "playing"
            else -> "idle"
        },
        transitionSpec = {
            when {
                targetState == "buffering" -> {
                    (scaleIn(
                        initialScale = 0.3f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ) + fadeIn(tween(200))) togetherWith
                            (scaleOut(
                                targetScale = 1.8f,
                                animationSpec = tween(250, easing = FastOutSlowInEasing)
                            ) + fadeOut(tween(200)))
                }

                initialState == "buffering" -> {
                    (scaleIn(
                        initialScale = 1.8f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ) + fadeIn(tween(200))) togetherWith
                            (scaleOut(
                                targetScale = 0.3f,
                                animationSpec = tween(250, easing = FastOutSlowInEasing)
                            ) + fadeOut(tween(200)))
                }

                else -> {
                    (scaleIn(
                        initialScale = 0.3f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ) + fadeIn(tween(300))) togetherWith
                            (scaleOut(
                                targetScale = 0.3f,
                                animationSpec = tween(200)
                            ) + fadeOut(tween(200)))
                }
            }
        },
        label = "controlButton"
    ) { state ->
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            if (state == "buffering") {
                DotLoadingAnimation(
                    dotSize = 5.dp,
                    dotSpacing = 3.dp,
                    color = colorScheme.tertiary,
                    animationDelay = 150,
                    animationDuration = 400
                )
            } else {
                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) stringResource(R.string.player_pause) else stringResource(
                            R.string.player_play
                        ),
                        tint = colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
