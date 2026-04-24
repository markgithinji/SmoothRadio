package com.smoothradio.radio.feature.radiolist.ui

import android.util.Log
import android.util.Size
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.repeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.smoothradio.radio.core.domain.model.RadioStation

import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.smoothradio.radio.R

@Composable
fun RadioStationRow(
    station: RadioStation,
    isPlaying: Boolean,
    playbackState: String,
    onPlayClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isBuffering = isPlaying && (playbackState == "Buffering" || playbackState == "Preparing Audio")
    val isLivePlaying = isPlaying && playbackState == "Playing"
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme

    // Row background color animation
    val rowBackgroundColor by animateColorAsState(
        targetValue = when {
            isLivePlaying -> colorScheme.primary.copy(alpha = 0.08f)
            isBuffering -> colorScheme.tertiary.copy(alpha = 0.08f)
            else -> Color.Transparent
        },
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "rowBackgroundColor"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onPlayClick() }
            .background(rowBackgroundColor)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo
        val infiniteTransition = rememberInfiniteTransition()
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween<Float>(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )

        Box(
            modifier = Modifier
                .size(48.dp)
                .then(
                    if (isBuffering) Modifier.graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    } else Modifier
                )
        ) {
            Image(
                painter = painterResource(id = station.logoResource),
                contentDescription = "${station.stationName} logo",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )

            if (isBuffering) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colorScheme.primary.copy(alpha = 0.15f))
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Station Name and Details Column
        Column(modifier = Modifier.weight(1f)) {
            // First row: Name + Status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Station name
                val nameColor by animateColorAsState(
                    targetValue = when {
                        isLivePlaying -> colorScheme.primary
                        isBuffering -> colorScheme.tertiary
                        else -> colorScheme.onSurface
                    },
                    animationSpec = tween(500, easing = FastOutSlowInEasing),
                    label = "nameColor"
                )

                Text(
                    text = station.stationName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 15.sp,
                    color = nameColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Status indicator
                when {
                    isBuffering -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(3) { index ->
                                val delay = (index * 200)
                                val alpha by infiniteTransition.animateFloat(
                                    initialValue = 0.3f,
                                    targetValue = 1f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween<Float>(600, delayMillis = delay),
                                        repeatMode = RepeatMode.Reverse
                                    )
                                )
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(
                                            colorScheme.tertiary.copy(alpha = alpha)
                                        )
                                )
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "LOADING",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.tertiary,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Second row: Frequency and location
            val freqColor by animateColorAsState(
                targetValue = when {
                    isLivePlaying -> colorScheme.primary
                    isBuffering -> colorScheme.tertiary
                    else -> colorScheme.onSurfaceVariant
                },
                animationSpec = tween(500, easing = FastOutSlowInEasing),
                label = "freqColor"
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = station.frequency,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    color = freqColor
                )

                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant
                )

                Text(
                    text = station.location,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Action Buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isLivePlaying) {
                MiniWaveformVisualization(
                    modifier = Modifier.width(60.dp),
                    height = 16.dp,
                    color = colorScheme.primary
                )
            }

            IconButton(
                onClick = onFavoriteClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (station.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    tint = if (station.isFavorite) Color.Red else colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    // Divider
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        thickness = 0.5.dp,
        color = colorScheme.outline.copy(alpha = 0.1f)
    )
}

@Composable
fun MiniWaveformVisualization(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    color: Color
) {
    val infiniteTransition = rememberInfiniteTransition()
    val barCount = 8

    Row(
        modifier = modifier.height(height),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(barCount) { index ->
            val delay = (index * 60)
            val amplitude by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 0.8f,
                animationSpec = infiniteRepeatable(
                    animation = tween<Float>(400, delayMillis = delay),
                    repeatMode = RepeatMode.Reverse
                )
            )

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(amplitude)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color.copy(alpha = 0.7f))
            )
        }
    }
}