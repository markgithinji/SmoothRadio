package com.smoothradio.radio.feature.radiolist.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smoothradio.radio.RadioTopBar
import com.smoothradio.radio.core.ui.PlayerControlViewModel
import com.smoothradio.radio.core.ui.RadioViewModel

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

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            RadioTopBar(
                onViewToggleClick = { radioViewModel.toggleViewPreference() },
                isGridView = isGridView,
                searchQuery = searchQuery,
                onSearchQueryChange = { radioViewModel.updateSearchQuery(it) },
                isSearchActive = isSearchActive,
                onSearchActiveChange = { radioViewModel.setSearchActive(it) }
            )

            if (allStations.isEmpty()) {
                // Loading state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val infiniteTransition = rememberInfiniteTransition()
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(3) { index ->
                                val delay = (index * 200)
                                val scale by infiniteTransition.animateFloat(
                                    initialValue = 0.5f,
                                    targetValue = 1f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween<Float>(400, delayMillis = delay),
                                        repeatMode = RepeatMode.Reverse
                                    )
                                )
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .scale(scale)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading stations...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (filteredStations.isEmpty() && searchQuery.isNotEmpty()) {
                // No search results
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No stations found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Try a different search term",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridScrollState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            top = 12.dp,
                            end = 12.dp,
                            bottom = 100.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = filteredStations,
                            key = { it.id }
                        ) { station ->
                            RadioStationGridItem(
                                station = station,
                                isPlaying = playingStation?.id == station.id,
                                playbackState = playbackState,
                                onPlayClick = { playerControlViewModel.requestPlayStation(station) },
                                onFavoriteClick = {
                                    radioViewModel.toggleFavorite(station.id, !station.isFavorite)
                                }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listScrollState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 0.dp,
                            top = 8.dp,
                            end = 0.dp,
                            bottom = 100.dp
                        )
                    ) {
                        items(
                            items = filteredStations,
                            key = { it.id }
                        ) { station ->
                            RadioStationRow(
                                station = station,
                                isPlaying = playingStation?.id == station.id,
                                playbackState = playbackState,
                                onPlayClick = { playerControlViewModel.requestPlayStation(station) },
                                onFavoriteClick = {
                                    radioViewModel.toggleFavorite(station.id, !station.isFavorite)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Floating Mini Player
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
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