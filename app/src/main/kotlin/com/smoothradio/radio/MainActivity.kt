package com.smoothradio.radio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.ui.PlayCommand
import com.smoothradio.radio.core.ui.PlayerControlViewModel
import com.smoothradio.radio.core.ui.RadioViewModel
import com.smoothradio.radio.core.util.AdConfig
import com.smoothradio.radio.feature.discover.ui.DiscoverScreen
import com.smoothradio.radio.feature.player.PlayerScreen
import com.smoothradio.radio.feature.radiolist.ui.RadioStationsScreen
import com.smoothradio.radio.service.StreamService
import com.smoothradio.radio.ui.theme.SmoothRadioTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.jvm.java
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val eventReceiver: BroadcastReceiver = EventReceiver()
    private val playerControlViewModel: PlayerControlViewModel by viewModels()
    private val radioViewModel: RadioViewModel by viewModels()

    private val isMobileAdsInitializeCalled = AtomicBoolean(false)

    // Playback state
    private lateinit var serviceIntent: Intent
    private var interstitialAd: InterstitialAd? = null
    private var isShowingAd = false
    private var adFailedCountdown = 0
    private var canShowAd: Boolean = true
    private var isPlaying = false
    private var currentStation: RadioStation? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        installSplashScreen()

        serviceIntent = Intent(this, StreamService::class.java)

        showConsentForm()
        setupBroadcastReceiver()

        setContent {
            SmoothRadioTheme {
                RadioApp(
                    playerControlViewModel = playerControlViewModel,
                    radioViewModel = radioViewModel
                )
            }
        }

        collectPlaybackFlows()
    }

    @Composable
    fun RadioApp(
        playerControlViewModel: PlayerControlViewModel,
        radioViewModel: RadioViewModel
    ) {
        var selectedTab by remember { mutableStateOf(0) }
        val playingStation by playerControlViewModel.playingStation.collectAsState()
        val playbackState by playerControlViewModel.playbackState.collectAsState()

        // Update current station when playing station changes
        LaunchedEffect(playingStation) {
            currentStation = playingStation
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                Column {
                    // Bottom Navigation Bar
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

    private fun setupBroadcastReceiver() {
        val eventFilter = IntentFilter(StreamService.ACTION_EVENT_CHANGE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(eventReceiver, eventFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(eventReceiver, eventFilter)
        }
    }

    private fun collectPlaybackFlows() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    playerControlViewModel.playCommand.collect { command ->
                        when (command) {
                            is PlayCommand.PlayStation -> {
                                currentStation = command.station
                                if (command.station.isPlaying) {
                                    playOrStop()
                                } else {
                                    startNewPlay()
                                }
                                playerControlViewModel.savePlayingStationId(command.station.id)
                            }
                            is PlayCommand.Refresh -> {
                                refresh()
                            }
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                playerControlViewModel.canShowAd.collect { canShow ->
                    canShowAd = canShow
                }
            }
        }
    }

    private fun startNewPlay() {
        isPlaying = true
        if (isShowingAd) return

        serviceIntent.action = StreamService.ACTION_SHOW_AD
        startStreamService()
        loadInterstitialAd()
        checkInternet()
    }

    private fun playOrStop() {
        if (isPlaying) {
            stopService(serviceIntent)
            isPlaying = false
            playerControlViewModel.updatePlaybackState(StreamService.StreamStates.IDLE)
            return
        }

        if (isShowingAd) return

        isPlaying = true
        serviceIntent.action = StreamService.ACTION_SHOW_AD
        startStreamService()
        loadInterstitialAd()
        checkInternet()
    }

    private fun refresh() {
        if (isShowingAd) return

        isPlaying = true
        serviceIntent.action = StreamService.ACTION_SHOW_AD
        startStreamService()
        loadInterstitialAd()
        checkInternet()
    }

    private fun startStreamService() {
        serviceIntent.putExtra(StreamService.EXTRA_LINK, currentStation?.streamLink)
        serviceIntent.putExtra(StreamService.EXTRA_LOGO, currentStation?.logoResource)
        serviceIntent.putExtra(StreamService.EXTRA_STATION_NAME, currentStation?.stationName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun playOnly() {
        serviceIntent.action = StreamService.ACTION_START
        startStreamService()
        playerControlViewModel.updatePlaybackState(StreamService.StreamStates.PREPARING)
        isPlaying = true
    }

    private fun loadInterstitialAd() {
        isShowingAd = true

        if (interstitialAd != null) {
            if (isPlaying) showAd()
            return
        }

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            this,
            AdConfig.interstitialAdId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    if (isPlaying) showAd()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                    handleAdLoadFailure(loadAdError)
                }
            }
        )
    }

    private fun showAd() {
        if (!canShowAd) {
            isShowingAd = false
            playOnly()
            return
        }

        val ad = interstitialAd ?: run {
            loadInterstitialAd()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                playOnly()
                isShowingAd = false
                preloadInterstitialAd()
                playerControlViewModel.recordAdShown()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                interstitialAd = null
                isShowingAd = false
                stopService(serviceIntent)
            }
        }

        ad.show(this)
    }

    private fun handleAdLoadFailure(loadAdError: LoadAdError) {
        adFailedCountdown++
        if (adFailedCountdown < MAX_AD_LOAD_ATTEMPTS) {
            loadInterstitialAd()
        } else {
            adFailedCountdown = 0
            isShowingAd = false
            playOnly()
        }
    }

    private fun preloadInterstitialAd() {
        if (interstitialAd != null) return

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            this,
            AdConfig.interstitialAdId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    adFailedCountdown = 0
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                    when (loadAdError.code) {
                        AdRequest.ERROR_CODE_NETWORK_ERROR,
                        AdRequest.ERROR_CODE_INTERNAL_ERROR -> {
                            adFailedCountdown++
                            if (adFailedCountdown < MAX_AD_LOAD_ATTEMPTS) {
                                preloadInterstitialAd()
                            } else {
                                adFailedCountdown = 0
                            }
                        }
                    }
                }
            }
        )
    }

    private fun checkInternet() {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val network = cm.activeNetwork
        val capabilities = cm.getNetworkCapabilities(network)
        val connected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        if (!connected) {
            Toast.makeText(this, getString(R.string.check_internet), Toast.LENGTH_SHORT).show()
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

    private inner class EventReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getStringExtra(StreamService.EXTRA_STATE) ?: ""

            isPlaying = when (state) {
                StreamService.StreamStates.PREPARING,
                StreamService.StreamStates.PLAYING,
                StreamService.StreamStates.BUFFERING -> true
                StreamService.StreamStates.IDLE,
                StreamService.StreamStates.ENDED -> false
                else -> false
            }

            playerControlViewModel.updatePlaybackState(state)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(eventReceiver)
    }

    companion object {
        private const val MAX_AD_LOAD_ATTEMPTS = 2
    }
}