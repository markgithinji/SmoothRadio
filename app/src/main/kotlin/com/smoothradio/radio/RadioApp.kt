package com.smoothradio.radio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.smoothradio.radio.core.domain.model.ToastType
import com.smoothradio.radio.core.ui.PlayerControlViewModel
import com.smoothradio.radio.core.ui.RadioViewModel
import com.smoothradio.radio.core.ui.common.AppToast
import com.smoothradio.radio.feature.discover.ui.DiscoverScreen
import com.smoothradio.radio.feature.player.ui.PlayerScreen
import com.smoothradio.radio.feature.radiolist.ui.RadioStationsScreen

@Composable
fun RadioApp(
    playerControlViewModel: PlayerControlViewModel,
    radioViewModel: RadioViewModel
) {
    val selectedTab by radioViewModel.selectedTab.collectAsState()

    val listScrollState = rememberLazyListState()
    val gridScrollState = rememberLazyGridState()
    val discoverScrollState = rememberLazyListState()
    val discoverCategoryScrollStates = remember { mutableStateMapOf<String, LazyListState>() }

    var toastType by remember { mutableStateOf<ToastType>(ToastType.Info("")) }
    var isToastVisible by remember { mutableStateOf(false) }

    // Collect toast events
    LaunchedEffect(Unit) {
        playerControlViewModel.toastMessage.collect { type ->
            toastType = type
            isToastVisible = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                NavigationBar(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        listOf("Stations", "Live", "Discover").forEachIndexed { index, title ->
                            NavigationBarItem(
                                selected = selectedTab == index,
                                onClick = { radioViewModel.setSelectedTab(index) },
                                icon = {
                                    Icon(
                                        painter = painterResource(
                                            id = when (index) {
                                                0 -> R.drawable.ic_nav_stations
                                                1 -> R.drawable.ic_nav_live
                                                else -> R.drawable.ic_nav_discover
                                            }
                                        ),
                                        contentDescription = title,
                                        modifier = Modifier.size(26.dp)
                                    )
                                },
                                label = { Text(title) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (selectedTab) {
                    0 -> RadioStationsScreen(
                        radioViewModel = radioViewModel,
                        playerControlViewModel = playerControlViewModel,
                        listScrollState = listScrollState,
                        gridScrollState = gridScrollState
                    )

                    1 -> PlayerScreen(
                        playerControlViewModel = playerControlViewModel
                    )

                    2 -> DiscoverScreen(
                        radioViewModel = radioViewModel,
                        playerControlViewModel = playerControlViewModel,
                        discoverScrollState = discoverScrollState,
                        categoryScrollStates = discoverCategoryScrollStates
                    )
                }
            }
        }

        AppToast(
            toastType = toastType,
            isVisible = isToastVisible,
            onDismiss = { isToastVisible = false },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
        )
    }
}