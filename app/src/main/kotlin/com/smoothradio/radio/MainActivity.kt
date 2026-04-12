package com.smoothradio.radio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.smoothradio.radio.core.ui.PlayerControlViewModel
import com.smoothradio.radio.core.ui.RadioViewModel
import com.smoothradio.radio.feature.discover.ui.DiscoverScreen
import com.smoothradio.radio.feature.player.PlayerScreen
import com.smoothradio.radio.feature.radiolist.ui.RadioStationsScreen
import com.smoothradio.radio.ui.theme.SmoothRadioTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.atomic.AtomicBoolean

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val playerControlViewModel: PlayerControlViewModel by viewModels()
    private val radioViewModel: RadioViewModel by viewModels()

    private val isMobileAdsInitializeCalled = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        installSplashScreen()
        showConsentForm()

        setContent {
            SmoothRadioTheme {
                RadioApp(
                    playerControlViewModel = playerControlViewModel,
                    radioViewModel = radioViewModel
                )
            }
        }
    }

    @Composable
    fun RadioApp(
        playerControlViewModel: PlayerControlViewModel,
        radioViewModel: RadioViewModel
    ) {
        var selectedTab by remember { mutableStateOf(0) }
        val playingStation by playerControlViewModel.playingStation.collectAsState()
        val playbackState by playerControlViewModel.playbackState.collectAsState()

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                RadioTopBar(
                    onSearchClick = { /* Handle search */ },
                    onSortClick = { /* Handle sort */ },
                    onInfoClick = { /* Show about dialog */ }
                )
            },
            bottomBar = {
                Column {
                    NavigationBar(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        listOf("Stations", "Live", "Discover").forEachIndexed { index, title ->
                            NavigationBarItem(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                icon = {
                                    Icon(
                                        imageVector = when (index) {
                                            0 -> Icons.Default.Radio
                                            1 -> Icons.Default.MusicNote
                                            else -> Icons.Default.Explore
                                        },
                                        contentDescription = title
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
                        playerControlViewModel = playerControlViewModel
                    )

                    1 -> PlayerScreen(
                        playerControlViewModel = playerControlViewModel
                    )

                    2 -> DiscoverScreen(
                        radioViewModel = radioViewModel,
                        playerControlViewModel = playerControlViewModel
                    )
                }
            }
        }
    }

    private fun showConsentForm() {
        val params = ConsentRequestParameters.Builder().build()
        val consentInformation = UserMessagingPlatform.getConsentInformation(this)

        consentInformation.requestConsentInfoUpdate(
            this,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { _ ->
                    if (consentInformation.canRequestAds()) initializeMobileAdsSdk()
                }
            },
            {
                if (consentInformation.canRequestAds()) initializeMobileAdsSdk()
            }
        )

        if (consentInformation.canRequestAds()) initializeMobileAdsSdk()
    }

    private fun initializeMobileAdsSdk() {
        if (isMobileAdsInitializeCalled.getAndSet(true)) return
        MobileAds.initialize(this)
    }
}