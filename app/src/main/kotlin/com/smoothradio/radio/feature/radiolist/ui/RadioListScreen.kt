package com.smoothradio.radio.feature.radiolist.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.ui.PlayerControlViewModel
import com.smoothradio.radio.core.ui.RadioViewModel
import kotlin.collections.emptyList

@Composable
fun RadioStationsScreen(
    radioViewModel: RadioViewModel,
    playerControlViewModel: PlayerControlViewModel,
    modifier: Modifier = Modifier
) {
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
        // Main List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 0.dp,
                top = 8.dp,
                end = 0.dp,
                bottom = 80.dp  // Space for floating mini player
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
                    onFavoriteClick = { radioViewModel.toggleFavorite(station.id, !station.isFavorite) }
                )
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