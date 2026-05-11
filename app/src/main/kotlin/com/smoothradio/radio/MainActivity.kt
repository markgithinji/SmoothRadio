package com.smoothradio.radio

import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.google.firebase.analytics.FirebaseAnalytics
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.domain.model.ToastType
import com.smoothradio.radio.core.ui.common.AppToast
import com.smoothradio.radio.core.ui.PlayCommand
import com.smoothradio.radio.core.ui.PlayerControlViewModel
import com.smoothradio.radio.core.ui.RadioViewModel
import com.smoothradio.radio.core.util.AdConfig
import com.smoothradio.radio.feature.discover.ui.DiscoverScreen
import com.smoothradio.radio.feature.player.ui.PlayerScreen
import com.smoothradio.radio.feature.radiolist.ui.RadioStationsScreen
import com.smoothradio.radio.service.StreamService
import com.smoothradio.radio.ui.theme.SmoothRadioTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private var mediaController: MediaController? = null
    private val playerControlViewModel: PlayerControlViewModel by viewModels()
    private val radioViewModel: RadioViewModel by viewModels()

    private val isMobileAdsInitializeCalled = AtomicBoolean(false)
    private lateinit var firebaseAnalytics: FirebaseAnalytics

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
        setupSystemBars()

        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        serviceIntent = Intent(this, StreamService::class.java)

        showConsentForm()

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

    /**
     * Configures system bars (status bar and navigation bar) to match the app's surface color.
     */
    private fun setupSystemBars() {
        val isDark = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        val surfaceColor = if (isDark) {
            android.graphics.Color.parseColor("#1E1E1E")
        } else {
            android.graphics.Color.parseColor("#FFFFFF")
        }

        enableEdgeToEdge(
            statusBarStyle = if (isDark) {
                SystemBarStyle.dark(surfaceColor)
            } else {
                SystemBarStyle.light(surfaceColor, surfaceColor)
            }
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    override fun onStart() {
        super.onStart()
        connectToMediaController()
    }

    override fun onStop() {
        super.onStop()
        disconnectMediaController()
    }

    private fun connectToMediaController() {
        val sessionToken = SessionToken(this, ComponentName(this, StreamService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                mediaController = controllerFuture.get()
                mediaController?.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        isPlaying = when (state) {
                            Player.STATE_BUFFERING,
                            Player.STATE_READY -> true
                            Player.STATE_IDLE,
                            Player.STATE_ENDED -> false
                            else -> isPlaying
                        }
                        Log.d("MainActivityLogs", "MediaController state: $state → isPlaying=$isPlaying")
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        this@MainActivity.isPlaying = isPlaying
                        Log.d("MainActivityLogs", "MediaController isPlaying changed: $isPlaying")
                    }
                })

                isPlaying = mediaController!!.isPlaying
            } catch (e: Exception) {
                Log.e("MainActivityLogs", "MediaController connection failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun disconnectMediaController() {
        mediaController?.let {
            it.release()
            mediaController = null
        }
    }

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

    private fun sendFirebaseAnalytics(stationName: String) {
        val event = stationName.lowercase().replace(" ", "_")
        val bundle = Bundle().apply {
            putString("station_name", stationName)
        }
        firebaseAnalytics.logEvent(event, bundle)
    }

    private fun collectPlaybackFlows() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    playerControlViewModel.playingStation.collect { station ->
                        station?.let {
                            currentStation = it
                            Log.d("MainActivityLogs", "Synced currentStation from repo: ${it.stationName}")
                        }
                    }
                }

                launch {
                    playerControlViewModel.playCommand.collect { command ->
                        when (command) {
                            is PlayCommand.PlayStation -> {
                                val station = command.station
                                val repoState = playerControlViewModel.playbackState.value

                                Log.d("MainActivityLogs", "▶ Tap: ${station.stationName} | " +
                                        "station.isPlaying=${station.isPlaying} | " +
                                        "local=$isPlaying | " +
                                        "repo=$repoState | " +
                                        "sameStation=${currentStation?.id == station.id}")

                                currentStation = station
                                if (station.isPlaying) {
                                    Log.d("MainActivityLogs", "  → playOrStop()")
                                    playOrStop()
                                } else {
                                    Log.d("MainActivityLogs", "  → startNewPlay()")
                                    startNewPlay()
                                }
                                playerControlViewModel.savePlayingStationId(station.id)
                            }
                            is PlayCommand.Refresh -> refresh()
                            is PlayCommand.Next -> playNext()
                            is PlayCommand.Previous -> playPrevious()
                            is PlayCommand.SetSleepTimer -> setSleepTimer(command.minutes)
                            is PlayCommand.SetEqBand -> setEqualizerBand(command.band, command.level)
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

    private fun setEqualizerBand(band: Int, level: Short) {
        val intent = Intent(this, StreamService::class.java).apply {
            action = StreamService.ACTION_SET_EQ_BAND
            putExtra(StreamService.EXTRA_BAND, band)
            putExtra(StreamService.EXTRA_LEVEL, level)
        }
        startService(intent)
    }

    private fun setSleepTimer(minutes: Int) {
        val timeInMillis = System.currentTimeMillis() + (minutes * 60 * 1000L)
        val intent = Intent(StreamService.ACTION_SET_TIMER).apply {
            setPackage(packageName)
            putExtra(StreamService.EXTRA_TIME_IN_MILLIS, timeInMillis)
        }
        sendBroadcast(intent)
        playerControlViewModel.showToast(ToastType.Success("Sleep timer set for $minutes minutes"))
    }

    private fun playNext() = lifecycleScope.launch {
        val stations = radioViewModel.allStations.first()
        if (stations.isEmpty()) return@launch

        val currentIndex = stations.indexOfFirst { it.id == currentStation?.id }
        val nextIndex = when {
            currentIndex == -1 -> 0  // No station playing, start from beginning
            currentIndex < stations.lastIndex -> currentIndex + 1
            else -> 0  // Wrap around to first
        }

        val nextStation = stations[nextIndex]
        if (nextStation.id == currentStation?.id) return@launch

        currentStation = nextStation
        Log.d("MainActivityLogs", "playNext -> ${nextStation.stationName}")
        startNewPlay()
        playerControlViewModel.savePlayingStationId(nextStation.id)
    }

    private fun playPrevious() = lifecycleScope.launch {
        val stations = radioViewModel.allStations.first()
        if (stations.isEmpty()) return@launch

        val currentIndex = stations.indexOfFirst { it.id == currentStation?.id }
        val prevIndex = when {
            currentIndex == -1 -> stations.lastIndex  // No station playing, start from end
            currentIndex > 0 -> currentIndex - 1
            else -> stations.lastIndex  // Wrap around to last
        }

        val prevStation = stations[prevIndex]
        if (prevStation.id == currentStation?.id) return@launch

        currentStation = prevStation
        Log.d("MainActivityLogs", "playPrevious -> ${prevStation.stationName}")
        startNewPlay()
        playerControlViewModel.savePlayingStationId(prevStation.id)
    }

    private fun startNewPlay() {
        Log.d("MainActivityLogs", "startNewPlay | isPlaying=$isPlaying | isShowingAd=$isShowingAd")
        isPlaying = true
        if (isShowingAd) {
            Log.d("MainActivityLogs", "  → BLOCKED: ad showing")
            return
        }

        serviceIntent.action = StreamService.ACTION_SHOW_AD
        startStreamService()
        loadInterstitialAd()
        checkInternet()
        sendFirebaseAnalytics(currentStation?.stationName ?: "Unknown station")
    }

    private fun playOrStop() {
        if (isPlaying) {
            Log.d("MainActivityLogs", "  → STOP")
            isPlaying = false
            isShowingAd = false
            serviceIntent.action = StreamService.ACTION_STOP
            startService(serviceIntent)
            return
        }

        if (isShowingAd) {
            Log.d("MainActivityLogs", "  → BLOCKED: ad showing")
            return
        }

        Log.d("MainActivityLogs", "  → START")
        isPlaying = true
        isShowingAd = true
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
        if (!isPlaying) {
            Log.d("MainActivityLogs", "playOnly: user stopped - skipping")
            return
        }

        serviceIntent.action = StreamService.ACTION_START
        startStreamService()
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
                    Log.e("MainActivityLogs", "Ad failed to load: ${loadAdError.message} (code: ${loadAdError.code})")
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
                Log.e("MainActivityLogs", "Ad failed to show: ${adError.message} (code: ${adError.code})")
                interstitialAd = null
                isShowingAd = false
                stopService(serviceIntent)
            }
        }

        ad.show(this)
    }

    private fun handleAdLoadFailure(loadAdError: LoadAdError) {
        adFailedCountdown++
        Log.d("MainActivityLogs", "Handling ad load failure. Attempt: $adFailedCountdown/$MAX_AD_LOAD_ATTEMPTS")
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
                    Log.e("MainActivityLogs", "Preload ad failed to load: ${loadAdError.message} (code: ${loadAdError.code})")
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
        val connected =
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        if (!connected) {
            playerControlViewModel.showToast(ToastType.Error(getString(R.string.check_internet)))
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

    override fun onDestroy() {
        super.onDestroy()
        disconnectMediaController()
    }

    companion object {
        private const val MAX_AD_LOAD_ATTEMPTS = 2
    }
}