package com.smoothradio.radio.feature.radiolist.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.ui.DotLoadingAnimation
import com.smoothradio.radio.core.ui.FavoriteIcon

@Composable
fun RadioStationGridItem(
    station: RadioStation,
    isPlaying: Boolean,
    playbackState: String,
    onPlayClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier,
    gridItemWidth: Dp = 120.dp
) {
    val isBuffering = isPlaying && (playbackState == "Buffering" || playbackState == "Preparing Audio")
    val isLivePlaying = isPlaying && playbackState == "Playing"
    val colorScheme = MaterialTheme.colorScheme

    // Under 110dp: logo + name only (no fav, no status)
    // 110-135dp: logo + name + fav (no status)
    // Over 135dp: everything
    val isTiny = gridItemWidth < 110.dp
    val isSmall = gridItemWidth < 135.dp

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
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(animation = tween<Float>(800, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse)
    )
    val liveDotScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(animation = tween(600, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse)
    )
    val liveDotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(600, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse)
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

    Card(
        modifier = modifier.fillMaxWidth().aspectRatio(1f).clickable { onPlayClick() },
        shape = RoundedCornerShape(if (isTiny) 8.dp else 12.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(overlayColor)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(if (isTiny) 4.dp else 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Logo
                Box(
                    modifier = Modifier
                        .size(when { isTiny -> 36.dp; isSmall -> 44.dp; else -> 55.dp })
                        .then(if (isBuffering) Modifier.graphicsLayer { scaleX = pulseScale; scaleY = pulseScale } else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = station.logoResource),
                        contentDescription = "${station.stationName} logo",
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                    if (isBuffering) {
                        Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(colorScheme.primary.copy(alpha = 0.15f)))
                    }
                }

                Spacer(modifier = Modifier.height(if (isTiny) 2.dp else 4.dp))

                // Station Name
                Text(
                    text = station.stationName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                    fontSize = when { isTiny -> 8.sp; isSmall -> 10.sp; else -> 11.sp },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = nameColor,
                    modifier = Modifier.fillMaxWidth()
                )

                // Status + Fav
                if (!isTiny) {
                    Spacer(modifier = Modifier.height(4.dp))

                    // Status indicator
                    if (!isSmall) {
                        when {
                            isBuffering -> DotLoadingAnimation(dotSize = 6.dp, dotSpacing = 4.dp, color = colorScheme.tertiary, animationDelay = 150, animationDuration = 400)
                            isLivePlaying -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(6.dp).scale(liveDotScale).clip(CircleShape).background(colorScheme.primary.copy(alpha = liveDotAlpha)))
                                Text("LIVE", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, fontWeight = FontWeight.Medium, color = colorScheme.primary, maxLines = 1)
                            }
                            else -> Text(station.frequency, style = MaterialTheme.typography.bodySmall, fontSize = 9.sp, color = colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
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
}