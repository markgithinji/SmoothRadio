package com.smoothradio.radio.feature.radiolist.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smoothradio.radio.R
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.domain.model.StreamStates
import com.smoothradio.radio.core.ui.common.DotLoadingAnimation
import com.smoothradio.radio.core.ui.common.FavoriteIcon
import com.smoothradio.radio.core.ui.common.pulseAnimation
import com.smoothradio.radio.core.ui.common.rememberSafeLogoId

@Composable
fun RadioStationGridItem(
    station: RadioStation,
    isPlaying: Boolean,
    playbackState: StreamStates,
    onPlayClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier,
    gridItemWidth: Dp = 120.dp
) {
    val isBuffering =
        isPlaying && (playbackState is StreamStates.BUFFERING || playbackState is StreamStates.PREPARING)
    val isLivePlaying = isPlaying && playbackState is StreamStates.PLAYING
    val colorScheme = MaterialTheme.colorScheme

    val safeLogoId = rememberSafeLogoId(station.logoResource)

    // Under 95dp: logo + name only (no fav, no status)
    // 95-115dp: logo + name + fav (no status)
    // Over 115dp: everything
    val isTiny = gridItemWidth < 95.dp
    val isSmall = gridItemWidth < 115.dp

    val overlayColor by animateColorAsState(
        targetValue = when {
            isLivePlaying -> colorScheme.primary.copy(alpha = 0.08f)
            isBuffering -> colorScheme.tertiary.copy(alpha = 0.08f)
            else -> Color.Transparent
        },
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "overlayColor"
    )

    val infiniteTransition = rememberInfiniteTransition()

    val liveDotScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val liveDotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val nameColor by animateColorAsState(
        targetValue = when {
            isLivePlaying -> colorScheme.primary
            isBuffering -> colorScheme.tertiary
            else -> colorScheme.onSurface
        },
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "nameColor"
    )

    Box(
        modifier = modifier
            .testTag("radio_station_${station.id}")
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(if (isTiny) 6.dp else 10.dp))
            .background(colorScheme.surface)
            .background(overlayColor)
            .then(
                if (!isSystemInDarkTheme()) {
                    Modifier.border(
                        0.8.dp,
                        colorScheme.outline,
                        RoundedCornerShape(if (isTiny) 6.dp else 10.dp)
                    )
                } else Modifier
            )
            .clickable { onPlayClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isTiny) 4.dp else 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo
            Box(
                modifier = Modifier
                    .size(
                        when {
                            isTiny -> 36.dp; isSmall -> 44.dp; else -> 55.dp
                        }
                    )
                    .then(
                        if (!isSystemInDarkTheme()) {
                            Modifier.border(
                                0.5.dp,
                                colorScheme.outline.copy(alpha = 0.6f),
                                RoundedCornerShape(0.dp)
                            )
                        } else Modifier
                    )
                    .pulseAnimation(enabled = isBuffering),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = safeLogoId),
                    contentDescription = stringResource(
                        R.string.station_logo_content_description,
                        station.stationName
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp),
                    contentScale = ContentScale.Fit,
                    colorFilter = if (safeLogoId == R.drawable.ic_radio_default) {
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

            Spacer(modifier = Modifier.height(if (isTiny) 2.dp else 4.dp))

            // Station Name
            Text(
                text = station.stationName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                fontSize = when {
                    isTiny -> 8.sp; isSmall -> 10.sp; else -> 11.sp
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = nameColor,
                modifier = Modifier.fillMaxWidth()
            )

            // Status + Fav
            if (!isTiny) {
                Spacer(modifier = Modifier.height(2.dp))

                // Status indicator
                if (!isSmall) {
                    Box(
                        modifier = Modifier.height(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            isBuffering -> DotLoadingAnimation(
                                dotSize = 6.dp,
                                dotSpacing = 4.dp,
                                color = colorScheme.tertiary,
                                animationDelay = 150,
                                animationDuration = 400
                            )

                            isLivePlaying -> Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .scale(liveDotScale)
                                        .clip(CircleShape)
                                        .background(colorScheme.primary.copy(alpha = liveDotAlpha))
                                )
                                Text(
                                    stringResource(R.string.player_live_tag),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.primary,
                                    maxLines = 1
                                )
                            }

                            else -> Text(
                                station.frequency,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 9.sp,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }

                // Favorite Button
                FavoriteIcon(
                    isFavorite = station.isFavorite,
                    onFavoriteClick = onFavoriteClick,
                    buttonSize = if (isSmall) 24.dp else 28.dp,
                    iconSize = if (isSmall) 12.dp else 14.dp
                )
            }
        }
    }
}
