package com.smoothradio.radio

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
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
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
import com.google.firebase.analytics.FirebaseAnalytics
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.domain.model.StreamStates
import com.smoothradio.radio.core.domain.model.ToastType
import com.smoothradio.radio.core.ui.PlayCommand
import com.smoothradio.radio.core.ui.PlayerControlViewModel
import com.smoothradio.radio.core.util.AdConfig
import com.smoothradio.radio.core.ui.util.LogoMapper
import com.smoothradio.radio.service.StreamService
import com.smoothradio.radio.service.util.command.ServiceCommand
import com.smoothradio.radio.ui.theme.SmoothRadioTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val playerControlViewModel: PlayerControlViewModel by viewModels()

    private val isMobileAdsInitializeCalled = AtomicBoolean(false)
    private lateinit var firebaseAnalytics: FirebaseAnalytics

    // Playback state
    private var interstitialAd: InterstitialAd? = null
    private var currentAdRequestId = 0
    private var adFailedCountdown = 0
    private var canShowAd: Boolean = true
    private var isPlaying = false
    private var currentStation: RadioStation? = null
    private var pendingAdStationId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        installSplashScreen()
        setupSystemBars()

        firebaseAnalytics = FirebaseAnalytics.getInstance(this)

        setContent {
            SmoothRadioTheme {
                RadioApp()
            }
        }

        collectPlaybackFlows()
        showConsentForm()
    }

    /**
     * Configures system bars (status bar and navigation bar) to match the app's surface color.
     */
    private fun setupSystemBars() {
        val isDark = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        val surfaceColor = if (isDark) {
            "#1E1E1E".toColorInt()
        } else {
            "#FFFFFF".toColorInt()
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
                    playerControlViewModel.playbackState.collect { state ->
                        isPlaying = when (state) {
                            StreamStates.PLAYING,
                            StreamStates.BUFFERING,
                            StreamStates.PREPARING -> true
                            else -> false
                        }
                        Log.d(
                            "MainActivityLogs",
                            "StreamState: ${state.label} → isPlaying=$isPlaying"
                        )
                    }
                }

                launch {
                    playerControlViewModel.playingStation.collect { station ->
                        if (station != null) {
                            currentStation = station
                        }
                    }
                }

                launch {
                    playerControlViewModel.playCommand.collect { command ->
                        when (command) {
                            is PlayCommand.PlayStation -> {
                                currentStation = command.station
                                startNewPlay()
                            }
                            is PlayCommand.TogglePlayPause -> playOrStop()
                            is PlayCommand.Refresh -> refresh()
                            is PlayCommand.SetSleepTimer -> setSleepTimer(command.minutes)
                            is PlayCommand.SetEqBand -> setEqualizerBand(
                                command.band,
                                command.level
                            )
                            is PlayCommand.SeekTo -> seekTo(command.position)
                            PlayCommand.SeekBack -> seekBack()
                            PlayCommand.SeekForward -> seekForward()
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

    private fun seekTo(position: Long) {
        Log.d("SmoothSeek", "MainActivity.seekTo: $position")
        val intent = Intent(this, StreamService::class.java).apply {
            action = ServiceCommand.ACTION_SEEK_TO
            putExtra(ServiceCommand.EXTRA_POSITION, position)
        }
        startService(intent)
    }

    private fun seekBack() {
        Log.d("SmoothSeek", "MainActivity.seekBack")
        val intent = Intent(this, StreamService::class.java).apply {
            action = ServiceCommand.ACTION_SEEK_BACK
        }
        startService(intent)
    }

    private fun seekForward() {
        Log.d("SmoothSeek", "MainActivity.seekForward")
        val intent = Intent(this, StreamService::class.java).apply {
            action = ServiceCommand.ACTION_SEEK_FORWARD
        }
        startService(intent)
    }

    private fun setEqualizerBand(band: Int, level: Short) {
        val intent = Intent(this, StreamService::class.java).apply {
            action = ServiceCommand.ACTION_SET_EQ_BAND
            putExtra(ServiceCommand.EXTRA_BAND, band)
            putExtra(ServiceCommand.EXTRA_LEVEL, level)
        }
        startService(intent)
    }

    private fun setSleepTimer(minutes: Int) {
        val timeInMillis = System.currentTimeMillis() + (minutes * 60 * 1000L)
        val intent = Intent(ServiceCommand.ACTION_SET_TIMER).apply {
            setPackage(packageName)
            putExtra(ServiceCommand.EXTRA_TIME_IN_MILLIS, timeInMillis)
        }
        sendBroadcast(intent)
        playerControlViewModel.showToast(ToastType.Success("Sleep timer set for $minutes minutes"))
    }

    private fun playOrStop() {
        Log.d("MainActivityLogs", "playOrStop() called | isPlaying=$isPlaying | currentStation=${currentStation?.stationName}")

        if (isPlaying) {
            Log.d("MainActivityLogs", "  → STOP execution")
            currentAdRequestId++ // Invalidate any pending ad load requests
            pendingAdStationId = null

            val intent = Intent(this, StreamService::class.java).apply {
                action = ServiceCommand.ACTION_STOP
            }
            startService(intent)
            return
        }

        val station = currentStation ?: run {
            Log.e("MainActivityLogs", "playOrStop: No current station!")
            return
        }

        Log.d("MainActivityLogs", "  → START execution")
        pendingAdStationId = station.id
        startStreamService(ServiceCommand.ACTION_SHOW_AD, station)
        loadInterstitialAd()
        checkInternet()
    }

    private fun startNewPlay() {
        val station = currentStation ?: run {
            Log.e("MainActivityLogs", "startNewPlay: No current station!")
            return
        }

        Log.d("MainActivityLogs", "startNewPlay | station=${station.stationName} | isPlaying=$isPlaying")

        pendingAdStationId = station.id
        startStreamService(ServiceCommand.ACTION_SHOW_AD, station)
        loadInterstitialAd()
        checkInternet()
    }

    private fun refresh() {
        val station = currentStation ?: run {
            Log.e("MainActivityLogs", "refresh: No current station!")
            return
        }

        pendingAdStationId = station.id
        startStreamService(ServiceCommand.ACTION_SHOW_AD, station)
        loadInterstitialAd()
        checkInternet()
    }

    private fun startStreamService(action: String, station: RadioStation) {
        Log.d("MainActivityLogs", "startStreamService | Creating Intent for: ${station.stationName} | Action: $action")
        val intent = Intent(this, StreamService::class.java).apply {
            this.action = action
            putExtra(ServiceCommand.EXTRA_LINK, station.streamLink)
            putExtra(ServiceCommand.EXTRA_LOGO, LogoMapper.getLogoById(station.id))
            putExtra(ServiceCommand.EXTRA_STATION_NAME, station.stationName)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun playOnly() {
        val station = currentStation ?: run {
            Log.e("MainActivityLogs", "playOnly: No current station!")
            return
        }
        startStreamService(ServiceCommand.ACTION_START, station)
    }

    private fun loadInterstitialAd() {
        val station = currentStation ?: run {
            Log.e("MainActivityLogsAd", "loadInterstitialAd: No current station!")
            return
        }

        val requestId = ++currentAdRequestId
        val stationIdAtRequest = station.id

        Log.d(
            "MainActivityLogsAd",
            "loadInterstitialAd() called (reqId=$requestId) | station=$stationIdAtRequest"
        )

        if (interstitialAd != null) {
            Log.d("MainActivityLogsAd", "  → Ad already exists, showing now")
            showAd()
            return
        }

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            this,
            AdConfig.interstitialAdId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    if (requestId != currentAdRequestId || currentStation?.id != stationIdAtRequest) {
                        Log.d("MainActivityLogsAd", "  → Stale ad load ignored (reqId=$requestId)")
                        return
                    }
                    Log.d("MainActivityLogsAd", "Ad successfully loaded (reqId=$requestId)")
                    interstitialAd = ad
                    showAd()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    if (requestId != currentAdRequestId || currentStation?.id != stationIdAtRequest) return
                    Log.e(
                        "MainActivityLogsAd",
                        "Ad failed to load (reqId=$requestId): ${loadAdError.message}"
                    )
                    interstitialAd = null
                    handleAdLoadFailure()
                }
            }
        )
    }

    private fun showAd() {
        val station = currentStation ?: run {
            Log.d("MainActivityLogsAd", "showAd: No current station")
            playOnly()
            return
        }

        Log.d("MainActivityLogsAd", "showAd() called | pendingId=$pendingAdStationId | current=${station.id}")

        // Final sanity check: Is the station that triggered the ad still the one we are on?
        if (pendingAdStationId != null && station.id != pendingAdStationId) {
            Log.d("MainActivityLogsAd", "  → BLOCKED: Station changed during ad load")
            return
        }

        if (!canShowAd) {
            Log.d("MainActivityLogsAd", "  → BLOCKED: canShowAd is false")
            playOnly()
            return
        }

        val ad = interstitialAd ?: run {
            Log.d("MainActivityLogsAd", "  → BLOCKED: No ad ready")
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d("MainActivityLogsAd", "Ad dismissed by user")
                interstitialAd = null

                // Only start playback if we still have a pending station request
                if (pendingAdStationId != null && currentStation?.id == pendingAdStationId) {
                    playOnly()
                }

                pendingAdStationId = null
                preloadInterstitialAd()
                playerControlViewModel.recordAdShown()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e("MainActivityLogsAd", "Ad failed to show: ${adError.message}")
                interstitialAd = null
                if (pendingAdStationId != null && currentStation?.id == pendingAdStationId) {
                    playOnly()
                }
                pendingAdStationId = null
            }
        }

        ad.show(this)
    }

    private fun handleAdLoadFailure() {
        adFailedCountdown++
        Log.d(
            "MainActivityLogsAd",
            "Handling ad load failure. Attempt: $adFailedCountdown/$MAX_AD_LOAD_ATTEMPTS"
        )
        if (adFailedCountdown < MAX_AD_LOAD_ATTEMPTS) {
            loadInterstitialAd()
        } else {
            adFailedCountdown = 0
            playOnly()
        }
    }

    private fun preloadInterstitialAd() {
        Log.d("MainActivityLogsAd", "preloadInterstitialAd() called")
        if (interstitialAd != null) {
            Log.d("MainActivityLogsAd", "  → Skipping: Ad already preloaded")
            return
        }

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            this,
            AdConfig.interstitialAdId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d("MainActivityLogsAd", "Preload ad loaded successfully")
                    interstitialAd = ad
                    adFailedCountdown = 0
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e(
                        "MainActivityLogsAd",
                        "Preload ad failed to load: ${loadAdError.message}"
                    )
                    interstitialAd = null
                    when (loadAdError.code) {
                        AdRequest.ERROR_CODE_NETWORK_ERROR,
                        AdRequest.ERROR_CODE_INTERNAL_ERROR -> { // Avoid preloading during no ad fill errors
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
        Log.d("MainActivityLogsAd", "Initializing Mobile Ads SDK")
        MobileAds.initialize(this)
    }

    companion object {
        private const val MAX_AD_LOAD_ATTEMPTS = 2
    }
}