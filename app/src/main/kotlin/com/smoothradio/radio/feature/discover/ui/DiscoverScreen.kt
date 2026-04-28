package com.smoothradio.radio.feature.discover.ui

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

    // Toast state
    var toastMessage by remember { mutableStateOf("") }
    var isToastVisible by remember { mutableStateOf(false) }

    // Observe favorite limit events
    LaunchedEffect(Unit) {
        radioViewModel.favoriteLimitExceeded.collect { message ->
            toastMessage = message
            isToastVisible = true
        }
    }

    // Request state update when screen becomes visible
    LaunchedEffect(Unit) {
        playerControlViewModel.requestStateUpdate()
    }

    // Create categories
    val categories = remember(stations, favorites) {
        CategoryHelper.createCategories(stations)
    }

    // Initialize scroll states for each category if not already present
    categories.forEach { category ->
        if (!categoryScrollStates.containsKey(category.label)) {
            categoryScrollStates[category.label] = LazyListState()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SimpleTopBar(title = "DISCOVER")

            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    // Loading state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            DotLoadingAnimation()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Loading stations...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (categories.isEmpty()) {
                    // Empty state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Radio,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No stations available",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = discoverScrollState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = categories,
                            key = { it.label }
                        ) { category ->
                            if (category.categoryRadioStationList.isNotEmpty()) {
                                val rowScrollState = categoryScrollStates[category.label] ?: rememberLazyListState()

                                Column {
                                    Text(
                                        text = category.label,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )

                                    LazyRow(
                                        state = rowScrollState,
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(
                                            items = category.categoryRadioStationList,
                                            key = { it.id }
                                        ) { station ->
                                            if (category.label == "Your Favorites") {
                                                AnimatedFavoriteItem(
                                                    station = station,
                                                    isPlaying = playingStation?.id == station.id,
                                                    playbackState = playbackState,
                                                    onPlayClick = {
                                                        playerControlViewModel.requestPlayStation(station)
                                                    },
                                                    onFavoriteClick = {
                                                        radioViewModel.toggleFavorite(
                                                            station.id,
                                                            !station.isFavorite
                                                        )
                                                    }
                                                )
                                            } else {
                                                RadioStationGridItem(
                                                    station = station,
                                                    isPlaying = playingStation?.id == station.id,
                                                    playbackState = playbackState,
                                                    onPlayClick = {
                                                        playerControlViewModel.requestPlayStation(station)
                                                    },
                                                    onFavoriteClick = {
                                                        radioViewModel.toggleFavorite(
                                                            station.id,
                                                            !station.isFavorite
                                                        )
                                                    },
                                                    modifier = Modifier
                                                        .width(130.dp)
                                                        .height(150.dp)
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

        // Toast for favorite limit exceeded
        AppToast(
            toastType = ToastType.Error(toastMessage),
            isVisible = isToastVisible,
            onDismiss = { isToastVisible = false },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}

@Composable
fun AnimatedFavoriteItem(
    station: RadioStation,
    isPlaying: Boolean,
    playbackState: String,
    onPlayClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    // Track if item should be visible
    var isVisible by remember { mutableStateOf(true) }

    // Animate removal when favorite is toggled off
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        label = "alpha"
    )

    val animatedScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    // Handle favorite click with animation
    val handleFavoriteClick = {
        isVisible = false
    }

    // Trigger actual removal after animation completes
    LaunchedEffect(isVisible) {
        if (!isVisible) {
            delay(300)
            onFavoriteClick()
        }
    }

    Box(
        modifier = Modifier
            .width(130.dp)
            .height(150.dp)
            .graphicsLayer {
                alpha = animatedAlpha
                scaleX = animatedScale
                scaleY = animatedScale
            }
    ) {
        RadioStationGridItem(
            station = station,
            isPlaying = isPlaying,
            playbackState = playbackState,
            onPlayClick = onPlayClick,
            onFavoriteClick = handleFavoriteClick,
            modifier = Modifier.fillMaxSize()
        )
    }
}