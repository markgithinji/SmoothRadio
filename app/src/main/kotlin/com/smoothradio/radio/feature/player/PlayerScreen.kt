package com.smoothradio.radio.feature.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.smoothradio.radio.core.ui.PlayerControlViewModel
import com.smoothradio.radio.core.ui.RadioViewModel
import com.smoothradio.radio.service.StreamService
import kotlin.collections.emptyList

@Composable
fun PlayerScreen(
    playerControlViewModel: PlayerControlViewModel,
    modifier: Modifier = Modifier
) {
    val playingStation by playerControlViewModel.playingStation.collectAsStateWithLifecycle()
    val playbackState by playerControlViewModel.playbackState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme

    var metadata by remember { mutableStateOf("") }

    val animatedColor by animateColorAsState(
        targetValue = when {
            playbackState == "Playing" -> colorScheme.primary.copy(alpha = 0.15f)
            playbackState == "Buffering" || playbackState == "Preparing Audio" -> colorScheme.tertiary.copy(alpha = 0.15f)
            else -> colorScheme.surfaceVariant
        },
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "background color"
    )

    // Clear metadata when playback stops (not playing and not buffering)
    LaunchedEffect(playbackState) {
        if (playbackState != "Playing" &&
            playbackState != "Buffering" &&
            playbackState != "Preparing Audio") {
            metadata = ""
        }
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                metadata = intent?.getStringExtra(StreamService.EXTRA_TITLE) ?: ""
            }
        }
        val filter = IntentFilter(StreamService.ACTION_METADATA_CHANGE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
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
                .padding(24.dp)
        ) {
            val infiniteTransition = rememberInfiniteTransition()

            // Scale animation
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.03f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )

            Box(
                modifier = Modifier.size(280.dp),
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

                    // Expanding circles
                    Box(
                        modifier = Modifier
                            .size(280.dp * waveRadius1)
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
                            .size(280.dp * waveRadius2)
                            .align(Alignment.Center)
                            .clip(CircleShape)
                            .background(
                                colorScheme.primary.copy(
                                    alpha = (1f - waveRadius2) * 0.06f
                                )
                            )
                    )

                    // Ring outline
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
                            .size(280.dp * waveRadius1)
                            .align(Alignment.Center)
                            .clip(CircleShape)
                            .border(
                                width = 1.dp,
                                color = colorScheme.primary.copy(alpha = ringAlpha),
                                shape = CircleShape
                            )
                    )
                }

                // Logo Surface
                Surface(
                    shape = RoundedCornerShape(40.dp),
                    color = colorScheme.primary.copy(alpha = 0.08f),
                    modifier = Modifier
                        .size(200.dp)
                        .scale(if (playbackState == "Playing") scale else 1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = playingStation?.logoResource ?: R.drawable.playicon),
                            contentDescription = "${playingStation?.stationName} logo",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(40.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = playingStation?.stationName ?: "No station playing",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Status indicator with 3-dot loading
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
                        repeat(3) { index ->
                            val delay = (index * 200)
                            val alpha by infiniteTransition.animateFloat(
                                initialValue = 0.3f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(400, delayMillis = delay),
                                    repeatMode = RepeatMode.Reverse
                                )
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(colorScheme.tertiary.copy(alpha = alpha))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
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
                    else -> {
                        Text(
                            text = playbackState.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            letterSpacing = 1.5.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Metadata
            if (metadata.isNotEmpty() && playbackState == "Playing") {
                Text(
                    text = metadata,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            } else if (playbackState == "Playing") {
                // Small spacer when no metadata but playing
                Spacer(modifier = Modifier.height(24.dp))
            } else {
                Spacer(modifier = Modifier.height(24.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            // Playback Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { /* Implement previous */ },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        modifier = Modifier.size(32.dp),
                        tint = colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .shadow(
                            elevation = 16.dp,
                            shape = CircleShape,
                            ambientColor = colorScheme.primary,
                            spotColor = colorScheme.primary
                        )
                        .clip(CircleShape)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    colorScheme.primary,
                                    colorScheme.primary.copy(alpha = 0.8f)
                                )
                            )
                        )
                        .clickable {
                            playingStation?.let { playerControlViewModel.requestPlayStation(it) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playbackState == "Playing") Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackState == "Playing") "Pause" else "Play",
                        modifier = Modifier.size(48.dp),
                        tint = Color.White
                    )
                }

                IconButton(
                    onClick = { /* Implement next */ },
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
                        onClick = { /* Show timer dialog */ },
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
                        onClick = { /* Share station */ },
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
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                AndroidView(
                    factory = { ctx ->
                        AdView(ctx).apply {
                            adUnitId = "ca-app-pub-9799428944156340/4540584810"
                            setAdSize(AdSize.LARGE_BANNER)
                            loadAd(AdRequest.Builder().build())
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}