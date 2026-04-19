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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.smoothradio.radio.RadioTopBar
import com.smoothradio.radio.core.ui.PlayerControlViewModel
import com.smoothradio.radio.core.ui.RadioViewModel

@Composable
fun RadioStationsScreen(
    radioViewModel: RadioViewModel,
    playerControlViewModel: PlayerControlViewModel,
    modifier: Modifier = Modifier
) {

    LaunchedEffect(Unit) {
        radioViewModel.observeAndProcessRemoteLinks()
    }

    val stations by radioViewModel.allStations.collectAsState(initial = emptyList())
    val playbackState by playerControlViewModel.playbackState.collectAsState(initial = "Idle")
    val playingStation by playerControlViewModel.playingStation.collectAsState(initial = null)

    var searchQuery by remember { mutableStateOf("") }
    var currentSort by remember { mutableStateOf(SortType.POPULAR) }

    val filteredStations = remember(stations, searchQuery, currentSort) {
        stations
            .filter { it.stationName.contains(searchQuery, ignoreCase = true) }
            .let { list ->
                when (currentSort) {
                    SortType.POPULAR -> list
                    SortType.A_TO_Z -> list.sortedBy { it.stationName.lowercase() }
                    SortType.Z_TO_A -> list.sortedByDescending { it.stationName.lowercase() }
                    SortType.FAVORITES -> list.filter { it.isFavorite }
                }
            }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            RadioTopBar(
                onSearchClick = { /* Handle search */ },
                onSortClick = { /* Handle sort */ },
                onInfoClick = { /* Show about dialog */ }
            )

            // Content
            if (stations.isEmpty()) {
                // Show 3-dot loading state
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
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 0.dp,
                        top = 8.dp,
                        end = 0.dp,
                        bottom = 80.dp
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
                                radioViewModel.toggleFavorite(
                                    station.id,
                                    !station.isFavorite
                                )
                            }
                        )
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

enum class SortType(val displayName: String) {
    POPULAR("Most Popular"),
    A_TO_Z("A - Z"),
    Z_TO_A("Z - A"),
    FAVORITES("Favorites")
}