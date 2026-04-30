package com.smoothradio.radio.feature.radiolist.ui

import android.util.Log
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smoothradio.radio.RadioTopBar
import com.smoothradio.radio.core.ui.AppToast
import com.smoothradio.radio.core.ui.DotLoadingAnimation
import com.smoothradio.radio.core.ui.PlayerControlViewModel
import com.smoothradio.radio.core.ui.RadioViewModel
import com.smoothradio.radio.core.ui.ToastType
import com.smoothradio.radio.feature.about.ui.AboutDialog

@Composable
fun RadioStationsScreen(
    radioViewModel: RadioViewModel,
    playerControlViewModel: PlayerControlViewModel,
    listScrollState: LazyListState,
    gridScrollState: LazyGridState,
    modifier: Modifier = Modifier
) {
    val filteredStations by radioViewModel.filteredStations.collectAsStateWithLifecycle()
    val allStations by radioViewModel.allStations.collectAsStateWithLifecycle()
    val playbackState by playerControlViewModel.playbackState.collectAsStateWithLifecycle()
    val playingStation by playerControlViewModel.playingStation.collectAsStateWithLifecycle()
    val isGridView by radioViewModel.isGridView.collectAsStateWithLifecycle()
    val searchQuery by radioViewModel.searchQuery.collectAsStateWithLifecycle()
    val isSearchActive by radioViewModel.isSearchActive.collectAsStateWithLifecycle()

    var toastMessage by remember { mutableStateOf("") }
    var isToastVisible by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) { radioViewModel.observeAndProcessRemoteLinks() }
    LaunchedEffect(Unit) {
        radioViewModel.favoriteLimitExceeded.collect { message ->
            toastMessage = message
            isToastVisible = true
        }
    }
    LaunchedEffect(Unit) { playerControlViewModel.requestStateUpdate() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val showMiniPlayer = maxHeight > 400.dp
        val miniPlayerMaxWidth = when {
            maxWidth < 500.dp -> 600.dp   // Phone: no limit
            else -> 500.dp                 // Tablet/large: constrain width
        }

        val gridColumns = when {
            maxWidth < 360.dp -> 2   // Small phone/split screen
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
                    isGridView = isGridView,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { radioViewModel.updateSearchQuery(it) },
                    isSearchActive = isSearchActive,
                    onSearchActiveChange = { radioViewModel.setSearchActive(it) },
                    onAboutClick = { showAboutDialog = true }
                )

                if (allStations.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            DotLoadingAnimation()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Loading stations...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else if (filteredStations.isEmpty() && searchQuery.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.SearchOff, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No stations found", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Try a different search term", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    if (isGridView) {
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
                            items(items = filteredStations, key = { it.id }) { station ->
                                RadioStationGridItem(
                                    station = station,
                                    isPlaying = playingStation?.id == station.id,
                                    playbackState = playbackState,
                                    onPlayClick = { playerControlViewModel.requestPlayStation(station) },
                                    onFavoriteClick = { radioViewModel.toggleFavorite(station.id, !station.isFavorite) },
                                    gridItemWidth = gridItemWidth
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listScrollState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 0.dp, top = 8.dp, end = 0.dp, bottom = if (showMiniPlayer) 100.dp else 12.dp)
                        ) {
                            items(items = filteredStations, key = { it.id }) { station ->
                                RadioStationRow(
                                    station = station,
                                    isPlaying = playingStation?.id == station.id,
                                    playbackState = playbackState,
                                    onPlayClick = { playerControlViewModel.requestPlayStation(station) },
                                    onFavoriteClick = { radioViewModel.toggleFavorite(station.id, !station.isFavorite) }
                                )
                            }
                        }
                    }
                }
            }

            if (showMiniPlayer) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
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
                                    playingStation?.let { playerControlViewModel.requestPlayStation(it) }
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
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (showMiniPlayer) 100.dp else 16.dp)
            )
        }
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false }, context = context)
    }
}