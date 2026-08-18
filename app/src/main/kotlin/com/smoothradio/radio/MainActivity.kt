package com.smoothradio.radio

import android.content.Intent
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
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
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.domain.model.StreamStates
import com.smoothradio.radio.core.domain.model.ToastType
import com.smoothradio.radio.core.ui.PlayCommand
import com.smoothradio.radio.core.ui.PlayerControlViewModel
import com.smoothradio.radio.core.ui.util.LogoMapper
import com.smoothradio.radio.core.util.AdConfig
import com.smoothradio.radio.service.StreamService
import com.smoothradio.radio.service.util.command.ServiceCommand
import com.smoothradio.radio.ui.theme.SmoothRadioTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val playerControlViewModel: PlayerControlViewModel by viewModels()

    private val isMobileAdsInitializeCalled = AtomicBoolean(false)
    private val firebaseAnalytics: FirebaseAnalytics by lazy { Firebase.analytics }

    private val serviceIntent by lazy { Intent(this, StreamService::class.java) }

    // Playback state
    private var interstitialAd: InterstitialAd? = null
    private val currentAdRequestId = AtomicInteger(0)
    private var adFailedCountdown = 0
    private var isPlaying = false
    private var currentStation: RadioStation? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        installSplashScreen()
        setupSystemBars()

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
        if (BuildConfig.DEBUG) return
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

                            StreamStates.PAUSED,
                            StreamStates.IDLE,
                            StreamStates.ENDED -> false
                        }
                    }
                }

                launch {
                    playerControlViewModel.playingStation.collect { station ->
                        if (station != null) {
                            currentStation = station
                            sendFirebaseAnalytics(station.stationName)
                        }
                    }
                }

                launch {
                    playerControlViewModel.playCommand.collect { command ->
                        when (command) {
                            is PlayCommand.PlayStation -> {
                                currentStation = command.station
                                initiatePlayback(PlaybackMode.NEW_PLAY)
                            }

                            is PlayCommand.TogglePlayPause -> {
                                initiatePlayback(PlaybackMode.TOGGLE)
                            }

                            is PlayCommand.Refresh -> {
                                initiatePlayback(PlaybackMode.NEW_PLAY)
                            }

                            is PlayCommand.SetSleepTimer -> setSleepTimer(command.minutes)
                            is PlayCommand.SetEqBand -> setEqualizerBand(
                                command.band,
                                command.level
                            )
                            is PlayCommand.ToggleEq -> toggleEqualizer(command.enabled)

                            is PlayCommand.SeekTo -> seekTo(command.position)
                            PlayCommand.SeekBack -> seekBack()
                            PlayCommand.SeekForward -> seekForward()
                        }
                    }
                }
            }
        }
    }

    private fun seekTo(position: Long) {
        val intent = Intent(this, StreamService::class.java).apply {
            action = ServiceCommand.ACTION_SEEK_TO
            putExtra(ServiceCommand.EXTRA_POSITION, position)
        }
        startService(intent)
    }

    private fun seekBack() {
        val intent = Intent(this, StreamService::class.java).apply {
            action = ServiceCommand.ACTION_SEEK_BACK
        }
        startService(intent)
    }

    private fun seekForward() {
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

    private fun toggleEqualizer(enabled: Boolean) {
        val intent = Intent(this, StreamService::class.java).apply {
            action = ServiceCommand.ACTION_TOGGLE_EQ
            putExtra(ServiceCommand.EXTRA_ENABLED, enabled)
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
        if (currentStation == null) return

        // Handle stopping for toggle mode
        if (mode == PlaybackMode.TOGGLE && isPlaying) {
            currentAdRequestId.incrementAndGet() // Invalidate any pending ad load requests immediately
            serviceIntent.action = ServiceCommand.ACTION_STOP
            startService(serviceIntent)
            return
        }

        // Guard against duplicate ad requests
//        if (serviceIntent.action == ServiceCommand.ACTION_SHOW_AD) return

        adFailedCountdown = 0
        serviceIntent.action = ServiceCommand.ACTION_SHOW_AD
        startStreamService()
        loadInterstitialAd()

        checkInternet()
    }

    private fun startStreamService() {
        val station = currentStation ?: return

        serviceIntent.putExtra(ServiceCommand.EXTRA_LINK, station.streamLink)
        serviceIntent.putExtra(ServiceCommand.EXTRA_LOGO, LogoMapper.getLogoById(station.id))
        serviceIntent.putExtra(ServiceCommand.EXTRA_STATION_NAME, station.stationName)
        serviceIntent.putExtra(ServiceCommand.EXTRA_STATION_ID, station.id)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun playOnly() {
        serviceIntent.action = ServiceCommand.ACTION_START
        startStreamService()
    }

    private fun loadInterstitialAd() {
        val station = currentStation ?: return

        val requestId = currentAdRequestId.incrementAndGet()
        val stationIdAtRequest = station.id

        if (interstitialAd != null) {
            if (serviceIntent.action == ServiceCommand.ACTION_SHOW_AD) {
                showAd(requestId)
            }
            return
        }

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            this,
            AdConfig.interstitialAdId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    if (requestId != currentAdRequestId.get() || currentStation?.id != stationIdAtRequest) {
                        return
                    }
                    interstitialAd = ad
                    if (serviceIntent.action == ServiceCommand.ACTION_SHOW_AD) {
                        showAd(requestId)
                    }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    if (requestId != currentAdRequestId.get() || currentStation?.id != stationIdAtRequest) {
                        return
                    }
                    interstitialAd = null
                    handleAdLoadFailure()
                }
            }
        )
    }

    private fun showAd(requestId: Int) {
        if (requestId != currentAdRequestId.get()) {
            return
        }

        if (currentStation == null) {
            playOnly()
            return
        }

        if (!playerControlViewModel.canShowAd.value) {
            playOnly()
            return
        }

        val ad = interstitialAd ?: run {
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                // Start playback for whatever station is currently selected
                interstitialAd = null
                playOnly()
                preloadInterstitialAd()
                playerControlViewModel.recordAdShown()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                if (adError.message.contains("already", ignoreCase = true)) {
                    return
                }
                interstitialAd = null
                playOnly()
            }
        }

        ad.show(this)
    }

    private fun handleAdLoadFailure() {
        adFailedCountdown++
        if (adFailedCountdown < MAX_AD_LOAD_ATTEMPTS) {
            loadInterstitialAd()
        } else {
            adFailedCountdown = 0
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

    private enum class PlaybackMode {
        NEW_PLAY,   // Starting a new station (always show ad)
        TOGGLE      // Toggle play/pause on current station
    }

    companion object {
        private const val MAX_AD_LOAD_ATTEMPTS = 2
    }
}