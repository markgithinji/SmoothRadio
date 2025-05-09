package com.smoothradio.radio.core.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Build
import android.widget.Toast
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.smoothradio.radio.MainActivity
import com.smoothradio.radio.core.model.RadioStation
import com.smoothradio.radio.service.StreamService

class PlayerManager(private val activity: MainActivity) {

    private val serviceIntent = Intent(activity, StreamService::class.java)
    private val eventIntent = Intent(StreamService.ACTION_EVENT_CHANGE).setPackage(activity.packageName)
    private var interstitialAd: InterstitialAd? = null
    var isShowingAd = false
    private var isPlaying = false
    private var adFailedCountdown = 0
    private var radioStation: RadioStation? = null
    var state: String = ""

    private val MAX_AD_LOAD_ATTEMPTS = 2
    private val ERROR_CODE_INVALID_REQUEST = 1
    private val ERROR_CODE_NO_FILL = 3

    init {
        setupBroadcastReceiver()
    }

    fun setRadioStation(radioStation: RadioStation) {
        this.radioStation = radioStation
    }

    fun setIsPlaying(isPlaying: Boolean) {
        this.isPlaying = isPlaying
    }

    fun getIsShowingAd(): Boolean = isShowingAd

    fun refresh() {
        isPlaying = true
        if (isShowingAd) return

        serviceIntent.action = StreamService.ACTION_SHOW_AD
        startStreamService()
        loadInterstitialAd()
        checkInternet()
        Toast.makeText(activity, "Refreshed!", Toast.LENGTH_SHORT).show()
    }

    fun playFromMainActivity() {
        isPlaying = true
        if (isShowingAd) return

        serviceIntent.action = StreamService.ACTION_SHOW_AD
        startStreamService()
        loadInterstitialAd()
        checkInternet()
        activity.sendFirebaseAnalytics(radioStation?.stationName ?: "")
    }

    private fun playOnly() {
        serviceIntent.action = StreamService.ACTION_START
        serviceIntent.putExtra(StreamService.EXTRA_LINK, radioStation?.url)
        serviceIntent.putExtra(StreamService.EXTRA_LOGO, radioStation?.logoResource)
        serviceIntent.putExtra(StreamService.EXTRA_STATION_NAME, radioStation?.stationName)
        startStreamService()
        state = StreamService.StreamStates.PREPARING
        broadcastState(state)
        isPlaying = true
    }

    fun playOrStop() {
        if (isPlaying) {
            activity.stopService(serviceIntent)
            return
        }

        state = StreamService.StreamStates.PREPARING
        broadcastState(state)
        isPlaying = true

        if (isShowingAd) return

        loadInterstitialAd()
        checkInternet()
        activity.sendFirebaseAnalytics(radioStation?.stationName ?: "")
    }

    private fun startStreamService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.startForegroundService(serviceIntent)
        } else {
            activity.startService(serviceIntent)
        }
    }

    private fun loadInterstitialAd() {
        isShowingAd = true
        if (interstitialAd != null) {
            if (isPlaying) showAd()
            return
        }

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(activity, "ca-app-pub-979942", adRequest, object : InterstitialAdLoadCallback() {
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

    private fun showAd() {
        val ad = interstitialAd ?: run {
            loadInterstitialAd()
            return
        }

        isShowingAd = true
        ad.show(activity)
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
                activity.stopService(serviceIntent)
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

        activity.stopService(serviceIntent)
        state = ""
        broadcastState(state)
        isShowingAd = false
        adFailedCountdown = 0
        Toast.makeText(activity, "Please check your internet and try again", Toast.LENGTH_SHORT).show()
    }

    private fun preloadInterstitialAd() {
        if (interstitialAd == null) {
            val adRequest = AdRequest.Builder().build()
            InterstitialAd.load(activity, "ca-app-pub-9799", adRequest, object : InterstitialAdLoadCallback() {
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

    private fun broadcastState(state: String) {
        eventIntent.putExtra(StreamService.EXTRA_STATE, state)
        activity.sendBroadcast(eventIntent)
    }

    private fun checkInternet() {
        val cm = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nInfo: NetworkInfo? = cm.activeNetworkInfo
        val connected = nInfo != null && nInfo.isAvailable && nInfo.isConnectedOrConnecting
        if (!connected) {
            Toast.makeText(activity, "Check Internet", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupBroadcastReceiver() {
        val eventFilter = IntentFilter().apply {
            addAction(StreamService.ACTION_EVENT_CHANGE)
        }
        val eventReceiver = EventReceiver()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(eventReceiver, eventFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            activity.registerReceiver(eventReceiver, eventFilter)
        }
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
}
