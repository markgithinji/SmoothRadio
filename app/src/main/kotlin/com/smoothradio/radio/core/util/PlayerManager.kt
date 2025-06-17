package com.smoothradio.radio.core.util

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.widget.Toast
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdRequest.ERROR_CODE_INVALID_REQUEST
import com.google.android.gms.ads.AdRequest.ERROR_CODE_NO_FILL
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.smoothradio.radio.R
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.service.StreamService
import javax.inject.Singleton

/**
 * Manages the audio playback, interstitial ads, and communication with the [StreamService].
 *
 * This class is a Singleton and handles the lifecycle of the audio stream,
 * including starting, stopping, and handling ad display. It also manages
 * the registration and unregistration of a [BroadcastReceiver] to listen for
 * events from the [StreamService].
 */
@Singleton
class PlayerManager {

    private var activity: Activity? = null

    private lateinit var serviceIntent: Intent
    private lateinit var eventIntent: Intent
    private var isReceiverRegistered: Boolean = false
    private val eventReceiver: BroadcastReceiver = EventReceiver()

    private var interstitialAd: InterstitialAd? = null
    var isShowingAd = false
    private var adFailedCountdown = 0

    private var isPlaying = false
    private var radioStation: RadioStation? = null

    private var state: String = ""


    fun bindActivity(activity: Activity) {
        this.activity = activity
        setupBroadcastReceiver()
    }

    private fun setupBroadcastReceiver() {
        if (isReceiverRegistered) return

        serviceIntent = Intent(activity, StreamService::class.java)
        eventIntent = Intent(StreamService.ACTION_EVENT_CHANGE).setPackage(activity?.packageName)

        val eventFilter = IntentFilter().apply {
            addAction(StreamService.ACTION_EVENT_CHANGE)
        }

        activity?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.registerReceiver(eventReceiver, eventFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                it.registerReceiver(eventReceiver, eventFilter)
            }
            isReceiverRegistered = true
        }
    }

    fun unbindActivity() {
        activity = null
    }

    fun setRadioStation(radioStation: RadioStation) {
        this.radioStation = radioStation
    }

    fun refresh() {
        isPlaying = true
        if (isShowingAd) return

        serviceIntent.action = StreamService.ACTION_SHOW_AD
        startStreamService()
        loadInterstitialAd()
        checkInternet()
        activity?.let { showToast(it.getString(R.string.refreshed)) }
    }

    fun playFromMainActivity() {
        isPlaying = true
        if (isShowingAd) return

        serviceIntent.action = StreamService.ACTION_SHOW_AD
        startStreamService()
        loadInterstitialAd()
        checkInternet()
    }

    private fun playOnly() {
        serviceIntent.action = StreamService.ACTION_START
        serviceIntent.putExtra(StreamService.EXTRA_LINK, radioStation?.streamLink)
        serviceIntent.putExtra(StreamService.EXTRA_LOGO, radioStation?.logoResource)
        serviceIntent.putExtra(StreamService.EXTRA_STATION_NAME, radioStation?.stationName)
        startStreamService()
        state = StreamService.StreamStates.PREPARING
        broadcastState(state)
        isPlaying = true
    }

    fun playOrStop() {
        if (isPlaying) {
            activity?.stopService(serviceIntent)
            return
        }

        state = StreamService.StreamStates.PREPARING
        broadcastState(state)
        isPlaying = true

        if (isShowingAd) return

        loadInterstitialAd()
        checkInternet()
    }

    private fun startStreamService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity?.startForegroundService(serviceIntent)
        } else {
            activity?.startService(serviceIntent)
        }
    }

    private fun loadInterstitialAd() {
        isShowingAd = true
        if (interstitialAd != null) {
            if (isPlaying) showAd()
            return
        }

        val adRequest = AdRequest.Builder().build()
        activity?.let {
            InterstitialAd.load(
                it,
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
                })
        }
    }

    private fun showAd() {
        val ad = interstitialAd ?: run {
            loadInterstitialAd()
            return
        }

        isShowingAd = true
        activity?.let { ad.show(it) }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                playOnly()
                isShowingAd = false
                preloadInterstitialAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                interstitialAd = null
                isShowingAd = false
                activity?.stopService(serviceIntent)
            }
        }
    }

    private fun handleAdLoadFailure(loadAdError: LoadAdError) {
        if (loadAdError.code == ERROR_CODE_INVALID_REQUEST || loadAdError.code == ERROR_CODE_NO_FILL) {
            playOnly()
            isShowingAd = false
            adFailedCountdown = 0
        } else {
            countdownAdFailed()
        }
    }

    private fun countdownAdFailed() {
        adFailedCountdown++
        if (adFailedCountdown < MAX_AD_LOAD_ATTEMPTS) {
            loadInterstitialAd()
            return
        }

        activity?.stopService(serviceIntent)
        state = ""
        broadcastState(state)
        isShowingAd = false
        adFailedCountdown = 0
        activity?.let { showToast(it.getString(R.string.toast_ad_load_fail)) }
    }

    private fun preloadInterstitialAd() {
        if (interstitialAd == null) {
            val adRequest = AdRequest.Builder().build()
            activity?.let {
                InterstitialAd.load(
                    it,
                    AdConfig.interstitialAdId,
                    adRequest,
                    object : InterstitialAdLoadCallback() {
                        override fun onAdLoaded(ad: InterstitialAd) {
                            interstitialAd = ad
                        }

                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            interstitialAd = null
                            preloadInterstitialAd()
                        }
                    })
            }
        }
    }

    private fun broadcastState(state: String) {
        eventIntent.putExtra(StreamService.EXTRA_STATE, state)
        activity?.sendBroadcast(eventIntent)
    }

    private fun checkInternet() {
        val cm = activity?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val capabilities = cm.getNetworkCapabilities(network)
        val connected =
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        if (!connected) {
            activity?.let { showToast(it.getString(R.string.check_internet)) }
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
    }


    fun unregisterBroadcastReceiver() {
        activity?.unregisterReceiver(eventReceiver)
        isReceiverRegistered = false
    }

    inner class EventReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val state = intent?.getStringExtra(StreamService.EXTRA_STATE)
            isPlaying = when (state) {
                StreamService.StreamStates.PREPARING,
                StreamService.StreamStates.PLAYING,
                StreamService.StreamStates.BUFFERING -> true

                StreamService.StreamStates.IDLE,
                StreamService.StreamStates.ENDED -> false

                else -> false
            }
        }
    }

    companion object {
        private const val MAX_AD_LOAD_ATTEMPTS = 2
    }

}
