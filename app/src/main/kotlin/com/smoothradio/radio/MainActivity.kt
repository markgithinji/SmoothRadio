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
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
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
import com.smoothradio.radio.core.ui.PlayCommand
import com.smoothradio.radio.core.ui.PlayerControlViewModel
import com.smoothradio.radio.core.util.AdConfig
import com.smoothradio.radio.service.StreamService
import com.smoothradio.radio.ui.theme.SmoothRadioTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private var mediaController: MediaController? = null
    private val playerControlViewModel: PlayerControlViewModel by viewModels()

    private val isMobileAdsInitializeCalled = AtomicBoolean(false)
    private lateinit var firebaseAnalytics: FirebaseAnalytics

    // Playback state
    private lateinit var serviceIntent: Intent
    private var interstitialAd: InterstitialAd? = null
    private var isShowingAd = false
    private var currentAdRequestId = 0
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
                RadioApp()
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
                        Log.d(
                            "MainActivityLogs",
                            "MediaController state: $state → isPlaying=$isPlaying"
                        )
                    }

//                    override fun onIsPlayingChanged(isPlaying: Boolean) {
//                        this@MainActivity.isPlaying = isPlaying
//                        Log.d("MainActivityLogs", "MediaController isPlaying changed: $isPlaying")
//                    }
                })

//                isPlaying = mediaController!!.isPlaying
            } catch (e: Exception) {
                Log.e("MainActivityLogs", "MediaController connection failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun disconnectMediaController() {
        try {
            mediaController?.release()
        } catch (e: Exception) {
            Log.e("MainActivityLogs", "Error releasing MediaController", e)
        } finally {
            mediaController = null
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
                            Log.d(
                                "MainActivityLogs",
                                "Synced currentStation from repo: ${it.stationName}"
                            )
                        }
                    }
                }

                launch {
                    playerControlViewModel.playCommand.collect { command ->
                        when (command) {
                            is PlayCommand.PlayStation -> {
                                val station = command.station
                                val repoState = playerControlViewModel.playbackState.value

                                Log.d(
                                    "MainActivityLogs", "▶ Tap: ${station.stationName} | " +
                                            "station.isPlaying=${station.isPlaying} | " +
                                            "local=$isPlaying | " +
                                            "repo=$repoState | " +
                                            "sameStation=${currentStation?.id == station.id}"
                                )

                                currentStation = station
                                if (station.isPlaying) {
                                    Log.d("MainActivityLogs", "  → playOrStop()")
                                    playOrStop()
                                } else {
                                    Log.d("MainActivityLogs", "  → startNewPlay()")
                                    startNewPlay()
                                }
                            }

                            is PlayCommand.Refresh -> refresh()
                            is PlayCommand.SetSleepTimer -> setSleepTimer(command.minutes)
                            is PlayCommand.SetEqBand -> setEqualizerBand(
                                command.band,
                                command.level
                            )
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

    private fun startNewPlay() {
        Log.d("MainActivityLogs", "startNewPlay | isPlaying=$isPlaying | isShowingAd=$isShowingAd")
        isPlaying = true
        if (isShowingAd) {
            Log.d("MainActivityLogs", "  → BLOCKED: ad already in progress")
            return
        }

        isShowingAd = true
        serviceIntent.action = StreamService.ACTION_SHOW_AD
        startStreamService()
        loadInterstitialAd()
        checkInternet()
//        sendFirebaseAnalytics(currentStation?.stationName ?: "Unknown station") ////////////////////////////////////////////////////////////////////////////////////////////
    }

    private fun playOrStop() {
        if (isPlaying) {
            Log.d("MainActivityLogs", "  → STOP")
            isPlaying = false
            isShowingAd = false
            currentAdRequestId++ // Invalidate any pending ad load requests immediately
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
        isShowingAd = true
        serviceIntent.action = StreamService.ACTION_SHOW_AD
        startStreamService()
        loadInterstitialAd()
        checkInternet()
    }

    private fun startStreamService() {
        serviceIntent.putExtra(StreamService.EXTRA_LINK, currentStation?.streamLink)
        serviceIntent.putExtra(StreamService.EXTRA_LOGO, currentStation?.logoResource)
        serviceIntent.putExtra(StreamService.EXTRA_STATION_NAME, currentStation?.stationName)
        startService(serviceIntent)
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
        val requestId = ++currentAdRequestId
        Log.d("MainActivityLogsAd", "loadInterstitialAd() called (reqId=$requestId) | canShowAd=$canShowAd")
        isShowingAd = true

        if (interstitialAd != null) {
            Log.d("MainActivityLogsAd", "  → Ad already exists, showing now")
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
                    if (requestId != currentAdRequestId) {
                        Log.d("MainActivityLogsAd", "  → Stale ad load ignored (reqId=$requestId)")
                        return
                    }
                    Log.d("MainActivityLogsAd", "Ad successfully loaded (reqId=$requestId)")
                    interstitialAd = ad
                    if (isPlaying) showAd()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    if (requestId != currentAdRequestId) return
                    Log.e(
                        "MainActivityLogsAd",
                        "Ad failed to load (reqId=$requestId): ${loadAdError.message}"
                    )
                    interstitialAd = null
                    handleAdLoadFailure(loadAdError)
                }
            }
        )
    }

    private fun showAd() {
        Log.d("MainActivityLogsAd", "showAd() called")
        if (!canShowAd) {
            Log.d("MainActivityLogsAd", "  → BLOCKED: canShowAd is false")
            isShowingAd = false
            playOnly()
            return
        }

        val ad = interstitialAd ?: run {
            Log.d("MainActivityLogsAd", "  → BLOCKED: No ad ready, loading one")
            loadInterstitialAd()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d("MainActivityLogsAd", "Ad dismissed by user")
                interstitialAd = null
                playOnly()
                isShowingAd = false
                preloadInterstitialAd()
                playerControlViewModel.recordAdShown()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(
                    "MainActivityLogsAd",
                    "Ad failed to show: ${adError.message}"
                )
                interstitialAd = null
                isShowingAd = false
                stopService(serviceIntent)
            }

            override fun onAdShowedFullScreenContent() {
                Log.d("MainActivityLogsAd", "Ad is now showing full screen")
                isShowingAd = true
            }
        }

        ad.show(this)
    }

    private fun handleAdLoadFailure(loadAdError: LoadAdError) {
        adFailedCountdown++
        Log.d(
            "MainActivityLogsAd",
            "Handling ad load failure. Attempt: $adFailedCountdown/$MAX_AD_LOAD_ATTEMPTS"
        )
        if (adFailedCountdown < MAX_AD_LOAD_ATTEMPTS) {
            loadInterstitialAd()
        } else {
            adFailedCountdown = 0
            isShowingAd = false
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
        Log.d("MainActivityLogsAd", "Initializing Mobile Ads SDK")
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