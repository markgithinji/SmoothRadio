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

    private enum class PlaybackMode {
        NEW_PLAY,   // Starting a new station (always show ad)
        TOGGLE      // Toggle play/pause on current station
    }

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
                        Log.d("MainActivityLogs", "Received PlayCommand: $command")
                        when (command) {
                            is PlayCommand.PlayStation -> {
                                Log.d("MainActivityLogs", "  → PlayStation: ${command.station.stationName} (ID: ${command.station.id})")
                                currentStation = command.station
                                initiatePlayback(PlaybackMode.NEW_PLAY)
                            }
                            is PlayCommand.TogglePlayPause -> {
                                Log.d("MainActivityLogs", "  → TogglePlayPause")
                                initiatePlayback(PlaybackMode.TOGGLE)
                            }
                            is PlayCommand.Refresh -> {
                                Log.d("MainActivityLogs", "  → Refresh")
                                initiatePlayback(PlaybackMode.NEW_PLAY)
                            }
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
                    Log.d("MainActivityLogsAd", "canShowAd flow emitted: $canShow")
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

    private fun initiatePlayback(mode: PlaybackMode) {
        val station = currentStation ?: run {
            Log.e("MainActivityLogs", "initiatePlayback: BLOCKED - No current station!")
            return
        }

        Log.d("MainActivityLogs", "initiatePlayback START: mode=$mode, station=${station.stationName} (ID=${station.id}), isPlaying=$isPlaying, canShowAd=$canShowAd")

        // Handle toggle mode - stop if currently playing
        if (mode == PlaybackMode.TOGGLE && isPlaying) {
            Log.d("MainActivityLogs", "  → Toggling OFF (stopping service)")
            currentAdRequestId++ // Invalidate any pending ad load requests
            pendingAdStationId = null

            val intent = Intent(this, StreamService::class.java).apply {
                action = ServiceCommand.ACTION_STOP
            }
            startService(intent)
            Log.d("MainActivityLogs", "initiatePlayback END (Toggle OFF)")
            return
        }

        // Start playback (with ad for NEW_PLAY, without ad for TOGGLE resume)
        pendingAdStationId = station.id
        Log.d("MainActivityLogs", "  → Set pendingAdStationId = ${station.id}")

        if (mode == PlaybackMode.NEW_PLAY) {
            Log.d("MainActivityLogs", "  → NEW_PLAY path: Calling ACTION_SHOW_AD and loadInterstitialAd()")
            startStreamService(ServiceCommand.ACTION_SHOW_AD, station)
            loadInterstitialAd()
        } else {
            Log.d("MainActivityLogs", "  → TOGGLE (ON) path: Calling ACTION_START directly")
            startStreamService(ServiceCommand.ACTION_START, station)
        }

        checkInternet()
        Log.d("MainActivityLogs", "initiatePlayback END")
    }

    private fun startStreamService(action: String, station: RadioStation) {
        Log.d("MainActivityLogs", "startStreamService CALL: action=$action, station=${station.stationName} (ID=${station.id})")
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
            Log.e("MainActivityLogs", "playOnly: BLOCKED - No current station!")
            return
        }
        Log.d("MainActivityLogs", "playOnly CALL: Starting station ${station.stationName} (ID=${station.id})")
        startStreamService(ServiceCommand.ACTION_START, station)
    }

    private fun loadInterstitialAd() {
        val station = currentStation ?: run {
            Log.e("MainActivityLogsAd", "loadInterstitialAd: BLOCKED - No current station!")
            return
        }

        val requestId = ++currentAdRequestId
        val stationIdAtRequest = station.id

        Log.d(
            "MainActivityLogsAd",
            "loadInterstitialAd() START (reqId=$requestId) | station=$stationIdAtRequest"
        )

        if (interstitialAd != null) {
            Log.d("MainActivityLogsAd", "  → Ad already exists (preloaded or currently showing). Calling showAd().")
            showAd()
            return
        }

        Log.d("MainActivityLogsAd", "  → No ad cached. Calling InterstitialAd.load()")
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            this,
            AdConfig.interstitialAdId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    if (requestId != currentAdRequestId || currentStation?.id != stationIdAtRequest) {
                        Log.d("MainActivityLogsAd", "Ad Load Callback: Stale load ignored (reqId=$requestId, currentReqId=$currentAdRequestId)")
                        return
                    }
                    Log.d("MainActivityLogsAd", "Ad Load Callback: Success (reqId=$requestId). Calling showAd().")
                    interstitialAd = ad
                    showAd()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    if (requestId != currentAdRequestId || currentStation?.id != stationIdAtRequest) {
                        Log.d("MainActivityLogsAd", "Ad Load Callback: Failed but stale, ignoring. (reqId=$requestId)")
                        return
                    }
                    Log.e(
                        "MainActivityLogsAd",
                        "Ad Load Callback: FAILED (reqId=$requestId): ${loadAdError.message}"
                    )
                    interstitialAd = null
                    handleAdLoadFailure()
                }
            }
        )
    }

    private fun showAd() {
        val station = currentStation ?: run {
            Log.d("MainActivityLogsAd", "showAd: BLOCKED - No current station. Calling playOnly().")
            playOnly()
            return
        }

        Log.d("MainActivityLogsAd", "showAd() ENTER: pendingId=$pendingAdStationId, currentId=${station.id}, canShowAd=$canShowAd")

        // Final sanity check: Is the station that triggered the ad still the one we are on?
        if (pendingAdStationId != null && station.id != pendingAdStationId) {
            Log.d("MainActivityLogsAd", "  → BLOCKED: Station changed (pending=$pendingAdStationId != current=${station.id})")
            return
        }

        if (!canShowAd) {
            Log.d("MainActivityLogsAd", "  → BLOCKED: canShowAd is FALSE. Calling playOnly().")
            playOnly()
            return
        }

        val ad = interstitialAd ?: run {
            Log.d("MainActivityLogsAd", "  → BLOCKED: interstitialAd is NULL")
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d("MainActivityLogsAd", "Ad Callback: onAdDismissedFullScreenContent() ENTER")
                interstitialAd = null

                // Only start playback if we still have a pending station request
                if (pendingAdStationId != null && currentStation?.id == pendingAdStationId) {
                    Log.d("MainActivityLogsAd", "  → Station match (id=$pendingAdStationId). Starting playback via playOnly().")
                    playOnly()
                } else {
                    Log.d("MainActivityLogsAd", "  → Station MISMATCH or null (pending=$pendingAdStationId, current=${currentStation?.id}). skipping playback.")
                }

                pendingAdStationId = null
                preloadInterstitialAd()
                playerControlViewModel.recordAdShown()
                Log.d("MainActivityLogsAd", "Ad Callback: onAdDismissedFullScreenContent() EXIT")
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e("MainActivityLogsAd", "Ad Callback: onAdFailedToShowFullScreenContent() ERROR: ${adError.message}")
                interstitialAd = null
                
                // If the ad is already showing or being shown, DO NOT start playback. 
                // The onAdDismissed callback (either for this ad or the previous one) will handle it.
                if (adError.message.contains("already", ignoreCase = true)) {
                    Log.d("MainActivityLogsAd", "  → Ad already in progress. Blocking premature playback fallback.")
                    return
                }

                if (pendingAdStationId != null && currentStation?.id == pendingAdStationId) {
                    Log.d("MainActivityLogsAd", "  → Attempting playOnly() despite show failure")
                    playOnly()
                }
                pendingAdStationId = null
            }
        }

        Log.d("MainActivityLogsAd", "  → Calling ad.show(this)")
        ad.show(this)
    }

    private fun handleAdLoadFailure() {
        adFailedCountdown++
        Log.d(
            "MainActivityLogsAd",
            "handleAdLoadFailure: Attempt $adFailedCountdown/$MAX_AD_LOAD_ATTEMPTS"
        )
        if (adFailedCountdown < MAX_AD_LOAD_ATTEMPTS) {
            Log.d("MainActivityLogsAd", "  → Retrying loadInterstitialAd()")
            loadInterstitialAd()
        } else {
            Log.d("MainActivityLogsAd", "  → Max attempts reached. Calling playOnly().")
            adFailedCountdown = 0
            playOnly()
        }
    }

    private fun preloadInterstitialAd() {
        Log.d("MainActivityLogsAd", "preloadInterstitialAd() START")
        if (interstitialAd != null) {
            Log.d("MainActivityLogsAd", "  → Skipping: Ad already exists")
            return
        }

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            this,
            AdConfig.interstitialAdId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d("MainActivityLogsAd", "Preload Callback: Success")
                    interstitialAd = ad
                    adFailedCountdown = 0
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e(
                        "MainActivityLogsAd",
                        "Preload Callback: FAILED: ${loadAdError.message}"
                    )
                    interstitialAd = null
                    when (loadAdError.code) {
                        AdRequest.ERROR_CODE_NETWORK_ERROR,
                        AdRequest.ERROR_CODE_INTERNAL_ERROR -> {
                            adFailedCountdown++
                            if (adFailedCountdown < MAX_AD_LOAD_ATTEMPTS) {
                                Log.d("MainActivityLogsAd", "  → Retrying preload (attempt $adFailedCountdown)")
                                preloadInterstitialAd()
                            } else {
                                Log.d("MainActivityLogsAd", "  → Max preload retries reached")
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