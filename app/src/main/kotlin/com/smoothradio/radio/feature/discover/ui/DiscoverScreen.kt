package com.smoothradio.radio.feature.discover.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smoothradio.radio.R
import com.smoothradio.radio.core.domain.model.Category
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.domain.model.StreamStates
import com.smoothradio.radio.core.domain.model.ToastType
import com.smoothradio.radio.core.ui.PlayerControlViewModel
import com.smoothradio.radio.core.ui.RadioViewModel
import com.smoothradio.radio.core.ui.common.AppToast
import com.smoothradio.radio.core.ui.common.DotLoadingAnimation
import com.smoothradio.radio.core.ui.common.SimpleTopBar
import com.smoothradio.radio.feature.discover.util.CategoryHelper
import com.smoothradio.radio.feature.radiolist.ui.components.RadioStationGridItem
import kotlinx.coroutines.delay

@Composable
fun DiscoverScreen(
    discoverScrollState: LazyListState,
    categoryScrollStates: MutableMap<String, LazyListState>,
    modifier: Modifier = Modifier,
    radioViewModel: RadioViewModel = hiltViewModel(),
    playerControlViewModel: PlayerControlViewModel = hiltViewModel()
) {
    val stations by radioViewModel.allStations.collectAsStateWithLifecycle()
    val favorites by radioViewModel.favoriteStations.collectAsStateWithLifecycle()
    val playbackState by playerControlViewModel.playbackState.collectAsStateWithLifecycle()
    val playingStation by playerControlViewModel.playingStation.collectAsStateWithLifecycle()

    val isLoading = stations.isEmpty()

    var toastMessage by remember { mutableStateOf("") }
    var isToastVisible by remember { mutableStateOf(false) }

    val categories = remember(stations, favorites) {
        CategoryHelper.createCategories(stations)
    }

    LaunchedEffect(categories) {
        categories.forEach { category ->
            if (!categoryScrollStates.containsKey(category.id)) {
                categoryScrollStates[category.id] = LazyListState()
            }
        }
    }

    LaunchedEffect(Unit) {
        radioViewModel.favoriteLimitExceeded.collect { message ->
            toastMessage = message
            isToastVisible = true
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight

        val (visualItemWidth, gridItemWidth) = remember(screenWidth) {
            val gridColumns = when {
                screenWidth < 500.dp -> 3
                screenWidth < 700.dp -> 4
                screenWidth < 900.dp -> 5
                else -> 7
            }

            val horizontalSpacing = when (gridColumns) {
                2 -> 16.dp
                3 -> 12.dp
                4 -> 10.dp
                5 -> 8.dp
                else -> 6.dp
            }

            val totalHorizontalOffset = horizontalSpacing * (gridColumns + 1)
            val visualWidth = (screenWidth - totalHorizontalOffset) / gridColumns
            val slotWidth = screenWidth / gridColumns

            visualWidth to slotWidth
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                SimpleTopBar(title = stringResource(R.string.discover_title))

                Box(modifier = Modifier.fillMaxSize()) {
                    Crossfade(
                        targetState = when {
                            isLoading -> DiscoverState.Loading
                            categories.isEmpty() -> DiscoverState.Empty
                            else -> DiscoverState.Content
                        },
                        label = "discoverState"
                    ) { state ->
                        when (state) {
                            DiscoverState.Loading -> DiscoverLoadingView()
                            DiscoverState.Empty -> DiscoverEmptyView()
                            DiscoverState.Content -> DiscoverContent(
                                categories = categories,
                                discoverScrollState = discoverScrollState,
                                categoryScrollStates = categoryScrollStates,
                                playingStation = playingStation,
                                playbackState = playbackState,
                                screenHeight = screenHeight,
                                visualItemWidth = visualItemWidth,
                                gridItemWidth = gridItemWidth,
                                onPlayClick = { playerControlViewModel.requestPlayStation(it) },
                                onFavoriteClick = { station, isFavorite ->
                                    radioViewModel.toggleFavorite(station.id, isFavorite)
                                }
                            )
                        }
                    }
                }
            }

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
}

enum class DiscoverState {
    Loading, Empty, Content
}

@Composable
fun DiscoverLoadingView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DotLoadingAnimation()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.loading_stations),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DiscoverEmptyView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
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
                text = stringResource(R.string.no_stations_available),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DiscoverContent(
    categories: List<Category>,
    discoverScrollState: LazyListState,
    categoryScrollStates: Map<String, LazyListState>,
    playingStation: RadioStation?,
    playbackState: StreamStates,
    screenHeight: Dp,
    visualItemWidth: Dp,
    gridItemWidth: Dp,
    onPlayClick: (RadioStation) -> Unit,
    onFavoriteClick: (RadioStation, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = discoverScrollState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        items(items = categories, key = { it.id }) { category ->
            if (category.categoryRadioStationList.isNotEmpty()) {
                val rowScrollState = categoryScrollStates[category.id]
                    ?: rememberLazyListState()

                CategoryRow(
                    category = category,
                    scrollState = rowScrollState,
                    playingStation = playingStation,
                    playbackState = playbackState,
                    screenHeight = screenHeight,
                    visualItemWidth = visualItemWidth,
                    gridItemWidth = gridItemWidth,
                    onPlayClick = onPlayClick,
                    onFavoriteClick = onFavoriteClick
                )
            }
        }
    }
}

@Composable
fun CategoryRow(
    category: Category,
    scrollState: LazyListState,
    playingStation: RadioStation?,
    playbackState: StreamStates,
    screenHeight: Dp,
    visualItemWidth: Dp,
    gridItemWidth: Dp,
    onPlayClick: (RadioStation) -> Unit,
    onFavoriteClick: (RadioStation, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        val isFavorites = category.id == CategoryHelper.ID_FAVORITES
        val displayLabel = if (isFavorites) {
            stringResource(R.string.category_favorites)
        } else {
            category.label
        }

        Text(
            text = displayLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            fontSize = if (screenHeight < 400.dp) 12.sp else 14.sp,
            modifier = Modifier.padding(horizontal = 12 .dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            state = scrollState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = category.categoryRadioStationList,
                key = { it.id }) { station ->
                val isPlaying = playingStation?.id == station.id
                if (isFavorites) {
                    AnimatedFavoriteItem(
                        station = station,
                        isPlaying = isPlaying,
                        playbackState = playbackState,
                        onPlayClick = { onPlayClick(station) },
                        onFavoriteClick = { onFavoriteClick(station, !station.isFavorite) },
                        gridItemWidth = gridItemWidth,
                        visualItemWidth = visualItemWidth,
                        modifier = Modifier.animateItem(
                            placementSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioLowBouncy
                            )
                        )
                    )
                } else {
                    RadioStationGridItem(
                        station = station,
                        isPlaying = isPlaying,
                        playbackState = playbackState,
                        onPlayClick = { onPlayClick(station) },
                        onFavoriteClick = { onFavoriteClick(station, !station.isFavorite) },
                        gridItemWidth = gridItemWidth,
                        modifier = Modifier.width(visualItemWidth)
                    )
                }
            }
        }
    }
}

@Composable
fun AnimatedFavoriteItem(
    station: RadioStation,
    isPlaying: Boolean,
    playbackState: StreamStates,
    onPlayClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    gridItemWidth: Dp,
    visualItemWidth: Dp,
    modifier: Modifier = Modifier
) {
    var isRemoving by remember { mutableStateOf(false) }

    // Entrance and Exit scale animation
    val scale by animateFloatAsState(
        targetValue = if (isRemoving) 0.7f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "scale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isRemoving) 0f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "alpha"
    )

    // Trigger actual removal after a short delay for the scale-out to be visible
    LaunchedEffect(isRemoving) {
        if (isRemoving) {
            delay(150)
            onFavoriteClick()
        }
    }

    Box(
        modifier = modifier
            .width(visualItemWidth)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
    ) {
        RadioStationGridItem(
            station = station,
            isPlaying = isPlaying,
            playbackState = playbackState,
            onPlayClick = onPlayClick,
            onFavoriteClick = { isRemoving = true },
            gridItemWidth = gridItemWidth,
            modifier = Modifier.fillMaxSize()
        )
    }
}
