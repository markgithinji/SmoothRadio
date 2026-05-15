package com.smoothradio.radio.feature.radiolist.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SearchOff
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smoothradio.radio.R
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.domain.model.StreamStates
import com.smoothradio.radio.core.domain.model.ToastType
import com.smoothradio.radio.core.ui.PlayerControlViewModel
import com.smoothradio.radio.core.ui.RadioViewModel
import com.smoothradio.radio.core.ui.common.AppToast
import com.smoothradio.radio.core.ui.common.DotLoadingAnimation
import com.smoothradio.radio.feature.radiolist.ui.components.AboutDialog
import com.smoothradio.radio.feature.radiolist.ui.components.PersistentMiniPlayer
import com.smoothradio.radio.feature.radiolist.ui.components.RadioStationGridItem
import com.smoothradio.radio.feature.radiolist.ui.components.RadioStationRow
import com.smoothradio.radio.feature.radiolist.ui.components.RadioTopBar

@Composable
fun RadioStationsScreen(
    listScrollState: LazyListState,
    gridScrollState: LazyGridState,
    modifier: Modifier = Modifier,
    radioViewModel: RadioViewModel = hiltViewModel(),
    playerControlViewModel: PlayerControlViewModel = hiltViewModel()
) {
    val uiState by radioViewModel.uiState.collectAsStateWithLifecycle()
    val playbackState by playerControlViewModel.playbackState.collectAsStateWithLifecycle()
    val playingStation by playerControlViewModel.playingStation.collectAsStateWithLifecycle()

    var toastMessage by remember { mutableStateOf("") }
    var isToastVisible by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        radioViewModel.favoriteLimitExceeded.collect { message ->
            toastMessage = message
            isToastVisible = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val showMiniPlayer = maxHeight > 400.dp
        val miniPlayerMaxWidth = when {
            maxWidth < 500.dp -> 600.dp   // Phone: no limit
            else -> 500.dp                 // Tablet/large: constrain width
        }

        val gridColumns = when {
            maxWidth < 500.dp -> 3   // Normal phone
            maxWidth < 700.dp -> 4   // Large phone/small tablet
            maxWidth < 900.dp -> 5   // Tablet
            else -> 7                 // Large tablet/landscape
        }
        val gridItemWidth = maxWidth / gridColumns

        val horizontalSpacing = when (gridColumns) {
            2 -> 16.dp
            3 -> 12.dp
            4 -> 10.dp
            5 -> 8.dp
            else -> 6.dp
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                RadioTopBar(
                    onViewToggleClick = { radioViewModel.toggleViewPreference() },
                    isGridView = uiState.isGridView,
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChange = { radioViewModel.updateSearchQuery(it) },
                    isSearchActive = uiState.isSearchActive,
                    onSearchActiveChange = { radioViewModel.setSearchActive(it) },
                    onAboutClick = { showAboutDialog = true }
                )

                AnimatedContent(
                    targetState = when {
                        uiState.allStations.isEmpty() -> ScreenState.Loading
                        uiState.filteredStations.isEmpty() && uiState.searchQuery.isNotEmpty() -> ScreenState.Empty
                        uiState.isGridView -> ScreenState.Grid
                        else -> ScreenState.List
                    },
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith fadeOut(
                            animationSpec = tween(
                                300
                            )
                        )
                    },
                    label = "screenState"
                ) { state ->
                    when (state) {
                        ScreenState.Loading -> LoadingStationsContent()
                        ScreenState.Empty -> EmptySearchContent()
                        ScreenState.Grid -> RadioStationGridContent(
                            stations = uiState.filteredStations,
                            playingStation = playingStation,
                            playbackState = playbackState,
                            gridScrollState = gridScrollState,
                            gridColumns = gridColumns,
                            horizontalSpacing = horizontalSpacing,
                            gridItemWidth = gridItemWidth,
                            showMiniPlayer = showMiniPlayer,
                            onStationClick = { playerControlViewModel.requestPlayStation(it) },
                            onFavoriteClick = { id, fav -> radioViewModel.toggleFavorite(id, fav) }
                        )

                        ScreenState.List -> RadioStationListContent(
                            stations = uiState.filteredStations,
                            playingStation = playingStation,
                            playbackState = playbackState,
                            listScrollState = listScrollState,
                            showMiniPlayer = showMiniPlayer,
                            onStationClick = { playerControlViewModel.requestPlayStation(it) },
                            onFavoriteClick = { id, fav -> radioViewModel.toggleFavorite(id, fav) }
                        )
                    }
                }
            }

            if (showMiniPlayer) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { /* Consume clicks */ }
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentWidth(Alignment.CenterHorizontally)
                    ) {
                        Box(modifier = Modifier.widthIn(max = miniPlayerMaxWidth)) {
                            PersistentMiniPlayer(
                                station = playingStation,
                                playbackState = playbackState,
                                onPlayPauseClick = {
                                    playingStation?.let {
                                        playerControlViewModel.requestPlayStation(
                                            it
                                        )
                                    }
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
                    .padding(bottom = if (showMiniPlayer) 70.dp else 16.dp)
            )
        }
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false }, context = context)
    }
}

@Composable
private fun LoadingStationsContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
private fun EmptySearchContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.no_stations_found),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.search_try_different_term),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RadioStationGridContent(
    stations: List<RadioStation>,
    playingStation: RadioStation?,
    playbackState: StreamStates,
    gridScrollState: LazyGridState,
    gridColumns: Int,
    horizontalSpacing: Dp,
    gridItemWidth: Dp,
    showMiniPlayer: Boolean,
    onStationClick: (RadioStation) -> Unit,
    onFavoriteClick: (Int, Boolean) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(gridColumns),
        state = gridScrollState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = horizontalSpacing,
            top = 12.dp,
            end = horizontalSpacing,
            bottom = if (showMiniPlayer) 100.dp else 12.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items = stations, key = { it.id }) { station ->
            RadioStationGridItem(
                station = station,
                isPlaying = playingStation?.id == station.id,
                playbackState = playbackState,
                onPlayClick = { onStationClick(station) },
                onFavoriteClick = { onFavoriteClick(station.id, !station.isFavorite) },
                gridItemWidth = gridItemWidth
            )
        }
    }
}

@Composable
private fun RadioStationListContent(
    stations: List<RadioStation>,
    playingStation: RadioStation?,
    playbackState: StreamStates,
    listScrollState: LazyListState,
    showMiniPlayer: Boolean,
    onStationClick: (RadioStation) -> Unit,
    onFavoriteClick: (Int, Boolean) -> Unit
) {
    LazyColumn(
        state = listScrollState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 0.dp,
            top = 8.dp,
            end = 0.dp,
            bottom = if (showMiniPlayer) 100.dp else 12.dp
        )
    ) {
        items(items = stations, key = { it.id }) { station ->
            RadioStationRow(
                station = station,
                isPlaying = playingStation?.id == station.id,
                playbackState = playbackState,
                onPlayClick = { onStationClick(station) },
                onFavoriteClick = { onFavoriteClick(station.id, !station.isFavorite) }
            )
        }
    }
}

private enum class ScreenState {
    Loading, Empty, Grid, List
}
