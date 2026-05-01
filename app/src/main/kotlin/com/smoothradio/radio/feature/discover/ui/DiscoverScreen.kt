package com.smoothradio.radio.feature.discover.ui

import android.util.Log
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.ui.AppToast
import com.smoothradio.radio.core.ui.DotLoadingAnimation
import com.smoothradio.radio.core.ui.PlayerControlViewModel
import com.smoothradio.radio.core.ui.RadioViewModel
import com.smoothradio.radio.core.ui.SimpleTopBar
import com.smoothradio.radio.core.ui.ToastType
import com.smoothradio.radio.feature.discover.util.CategoryHelper
import com.smoothradio.radio.feature.radiolist.ui.RadioStationGridItem
import kotlinx.coroutines.delay

@Composable
fun DiscoverScreen(
    radioViewModel: RadioViewModel,
    playerControlViewModel: PlayerControlViewModel,
    discoverScrollState: LazyListState,
    categoryScrollStates: MutableMap<String, LazyListState>,
    modifier: Modifier = Modifier
) {
    val stations by radioViewModel.allStations.collectAsStateWithLifecycle()
    val favorites by radioViewModel.favoriteStations.collectAsStateWithLifecycle()
    val playbackState by playerControlViewModel.playbackState.collectAsStateWithLifecycle()
    val playingStation by playerControlViewModel.playingStation.collectAsStateWithLifecycle()

    val isLoading = stations.isEmpty()

    var toastMessage by remember { mutableStateOf("") }
    var isToastVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        radioViewModel.favoriteLimitExceeded.collect { message ->
            toastMessage = message
            isToastVisible = true
        }
    }

    LaunchedEffect(Unit) {
        playerControlViewModel.requestStateUpdate()
    }

    val categories = remember(stations, favorites) {
        CategoryHelper.createCategories(stations)
    }

    categories.forEach { category ->
        if (!categoryScrollStates.containsKey(category.label)) {
            categoryScrollStates[category.label] = LazyListState()
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight

        val itemWidth = when {
            screenWidth < 300.dp -> 96.dp
            screenWidth < 400.dp -> 130.dp
            else -> 137.dp
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                SimpleTopBar(title = "DISCOVER")

                Box(modifier = Modifier.fillMaxSize()) {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                DotLoadingAnimation()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Loading stations...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else if (categories.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Radio, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("No stations available", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        LazyColumn(
                            state = discoverScrollState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            items(items = categories, key = { it.label }) { category ->
                                if (category.categoryRadioStationList.isNotEmpty()) {
                                    val rowScrollState = categoryScrollStates[category.label] ?: rememberLazyListState()

                                    Column {
                                        Text(
                                            text = category.label,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = if (screenHeight < 400.dp) 12.sp else 14.sp,
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        LazyRow(
                                            state = rowScrollState,
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            items(items = category.categoryRadioStationList, key = { it.id }) { station ->
                                                if (category.label == "Your Favorites") {
                                                    AnimatedFavoriteItem(
                                                        station = station,
                                                        isPlaying = playingStation?.id == station.id,
                                                        playbackState = playbackState,
                                                        onPlayClick = { playerControlViewModel.requestPlayStation(station) },
                                                        onFavoriteClick = { radioViewModel.toggleFavorite(station.id, !station.isFavorite) },
                                                        gridItemWidth = itemWidth,
                                                        modifier = Modifier.width(itemWidth)
                                                    )
                                                } else {
                                                    RadioStationGridItem(
                                                        station = station,
                                                        isPlaying = playingStation?.id == station.id,
                                                        playbackState = playbackState,
                                                        onPlayClick = { playerControlViewModel.requestPlayStation(station) },
                                                        onFavoriteClick = { radioViewModel.toggleFavorite(station.id, !station.isFavorite) },
                                                        gridItemWidth = itemWidth,
                                                        modifier = Modifier.width(itemWidth)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AppToast(
                toastType = ToastType.Error(toastMessage),
                isVisible = isToastVisible,
                onDismiss = { isToastVisible = false },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
            )
        }
    }
}

@Composable
fun AnimatedFavoriteItem(
    modifier: Modifier = Modifier,
    station: RadioStation,
    isPlaying: Boolean,
    playbackState: String,
    onPlayClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    gridItemWidth: Dp = 120.dp
) {
    var isVisible by remember { mutableStateOf(true) }

    // Animate removal when favorite is toggled off
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "alpha"
    )

    val animatedScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.8f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "scale"
    )

    val handleFavoriteClick = { isVisible = false }

    // Trigger actual removal after animation completes
    LaunchedEffect(isVisible) {
        if (!isVisible) {
            delay(300)
            onFavoriteClick()
        }
    }

    Box(
        modifier = modifier.graphicsLayer { alpha = animatedAlpha; scaleX = animatedScale; scaleY = animatedScale }
    ) {
        RadioStationGridItem(
            station = station,
            isPlaying = isPlaying,
            playbackState = playbackState,
            onPlayClick = onPlayClick,
            onFavoriteClick = handleFavoriteClick,
            gridItemWidth = gridItemWidth,
            modifier = Modifier.fillMaxSize()
        )
    }
}