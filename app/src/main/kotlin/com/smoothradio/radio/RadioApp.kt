package com.smoothradio.radio

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smoothradio.radio.core.domain.model.ToastType
import com.smoothradio.radio.core.ui.PlayerControlViewModel
import com.smoothradio.radio.core.ui.RadioViewModel
import com.smoothradio.radio.core.ui.common.AppToast
import com.smoothradio.radio.feature.discover.ui.DiscoverScreen
import com.smoothradio.radio.feature.player.ui.PlayerScreen
import com.smoothradio.radio.feature.radiolist.ui.RadioStationsScreen

@Composable
fun RadioApp(
    playerControlViewModel: PlayerControlViewModel = hiltViewModel(),
    radioViewModel: RadioViewModel = hiltViewModel()
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
                    listOf("Stations", "Live", "Discover").forEachIndexed { index, title ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val isSelected = selectedTab == index
                        
                        val scale by animateFloatAsState(
                            targetValue = when {
                                isPressed -> 0.88f
                                isSelected -> 1.12f
                                else -> 1f
                            },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "nav_icon_scale"
                        )

                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { radioViewModel.setSelectedTab(index) },
                            interactionSource = interactionSource,
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
                                    modifier = Modifier
                                        .size(26.dp)
                                        .graphicsLayer {
                                            val baseScale = if (index == 1) 0.95f else 0.85f
                                            scaleX = scale * baseScale
                                            scaleY = scale * baseScale
                                        }
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
        ) { paddingValues ->
            val playingStation by playerControlViewModel.playingStation.collectAsState()
            val isMiniPlayerVisible = selectedTab == 0 && playingStation != null

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (selectedTab) {
                    0 -> RadioStationsScreen(
                        listScrollState = listScrollState,
                        gridScrollState = gridScrollState
                    )

                    1 -> PlayerScreen()

                    2 -> DiscoverScreen(
                        discoverScrollState = discoverScrollState,
                        categoryScrollStates = discoverCategoryScrollStates
                    )
                }

                AppToast(
                    toastType = toastType,
                    isVisible = isToastVisible,
                    onDismiss = { isToastVisible = false },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (isMiniPlayerVisible) 70.dp else 16.dp)
                )
            }
        }
    }
}
