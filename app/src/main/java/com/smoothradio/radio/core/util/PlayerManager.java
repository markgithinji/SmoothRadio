package com.smoothradio.radio.core.util;

import static android.content.Context.RECEIVER_NOT_EXPORTED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.smoothradio.radio.MainActivity;
import com.smoothradio.radio.core.model.RadioStation;
import com.smoothradio.radio.service.StreamService;

public class PlayerManager {

    private final MainActivity activity;
    private final Intent serviceIntent;
    private final Intent eventIntent;
    private InterstitialAd interstitialAd;
    private boolean isShowingAd;
    private boolean isPlaying;
    private int adFailedCountdown = 0;
    private RadioStation radioStation;
    private static final int MAX_AD_LOAD_ATTEMPTS = 2;
    private static final int ERROR_CODE_INVALID_REQUEST = 1;
    private static final int ERROR_CODE_NO_FILL = 3;
    public String state = "";

    public PlayerManager(MainActivity activity) {
        this.activity = activity;
        this.serviceIntent = new Intent(activity, StreamService.class);
        this.eventIntent = new Intent(StreamService.ACTION_EVENT_CHANGE)
                .setPackage(activity.getPackageName());
        setupBroadcastReceiver();
    }

    public void setRadioStation(RadioStation radioStation) {
        this.radioStation = radioStation;
    }
    public void setISplaying(boolean isPlaying) {
        this.isPlaying = isPlaying;
    }
    public boolean getIsShowingAd()
    {
        return isShowingAd;
    }

    public void refresh()
    {
        // Update UI before play
        isPlaying = true;

        if (isShowingAd) {
            // Avoid multiple clicks while an ad is already loading
            return;
        }

        // stop from within service is faster than stopping the whole service as a whole
        serviceIntent.setAction(StreamService.ACTION_SHOW_AD);
        startStreamService();

        loadInterstitialAd();
        checkInternet();

        Toast.makeText(activity, "Refreshed!", Toast.LENGTH_SHORT).show();
    }


    public void playFromMainActivity() {
        isPlaying = true;

        if (isShowingAd) {
            // Prevent multiple clicks while loading ad
            return;
        }

        serviceIntent.setAction(StreamService.ACTION_SHOW_AD);
        startStreamService();

        loadInterstitialAd();
        checkInternet();

        // Log the event for analytics
        activity.sendFirebaseAnalytics(radioStation.getStationName());
    }

    private void playOnly() {
        serviceIntent.setAction(StreamService.ACTION_START);
        serviceIntent.putExtra(StreamService.EXTRA_LINK, radioStation.getUrl());
        serviceIntent.putExtra(StreamService.EXTRA_LOGO, radioStation.getLogoResource());
        serviceIntent.putExtra(StreamService.EXTRA_STATION_NAME, radioStation.getStationName());

        startStreamService();

        //update ui pre-play
        state = StreamService.StreamStates.PREPARING;
        broadcastState(state);

        isPlaying = true;
    }

    public void playOrStop() {
        if (isPlaying) {
            activity.stopService(serviceIntent);
            return;
        }

        // Start playing: update UI and state
        state = StreamService.StreamStates.PREPARING;
        broadcastState(state);
        isPlaying = true;

        if (isShowingAd) {
            // Prevent multiple clicks while ad is loading
            return;
        }

        loadInterstitialAd();
        checkInternet();

        // Log the event for analytics
        activity.sendFirebaseAnalytics(radioStation.getStationName());
    }


    private void startStreamService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.startForegroundService(serviceIntent);
            return;
        }
        activity.startService(serviceIntent);
    }

    private void loadInterstitialAd() {
        isShowingAd = true;
        if (interstitialAd != null) {
            // If the ad is already loaded, show it if the user has pressed play and hasn't stopped the service manually.
            if (isPlaying) showAd();
            return;
        }

        AdRequest interstitialAdRequest = new AdRequest.Builder().build();//ca-app-pub-9799428944156340/2070618771
        InterstitialAd.load(activity, "ca-app-pub-979942", interstitialAdRequest, new InterstitialAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                super.onAdLoaded(interstitialAd);
                PlayerManager.this.interstitialAd = interstitialAd;
                if (isPlaying)
                    showAd(); // If the ad is already loaded, show it if the user has pressed play and hasn't stopped the service manually.
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                super.onAdFailedToLoad(loadAdError);
                PlayerManager.this.interstitialAd = null;

                handleAdLoadFailure(loadAdError);
            }

        });
    }

    private void showAd() {
        if (interstitialAd == null) {
            loadInterstitialAd();
            return;
        }

        isShowingAd = true;
        interstitialAd.show(activity);
        PlayerManager.this.interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {

            @Override
            public void onAdDismissedFullScreenContent() {

                PlayerManager.this.interstitialAd = null;

                playOnly();
                isShowingAd = false;
                preloadInterstitialAd();
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {

                PlayerManager.this.interstitialAd = null;
                isShowingAd = false;
                activity.stopService(serviceIntent);
            }
        });
    }

    private void handleAdLoadFailure(LoadAdError loadAdError) {
        // only play when user has seen enough ads, no ad fill or internal admob error
        if (loadAdError.getCode() == ERROR_CODE_INVALID_REQUEST ||
                loadAdError.getCode() == ERROR_CODE_NO_FILL) {
            playOnly();
            isShowingAd = false;
            adFailedCountdown = 0;
        } else {
            countdownAdFailed();
        }
    }
    private void countdownAdFailed() {
        adFailedCountdown++;

        if (adFailedCountdown < MAX_AD_LOAD_ATTEMPTS) {
            // Retry loading the ad
            loadInterstitialAd();
            return;
        }

        // Max attempts reached — stop trying and reset
        activity.stopService(serviceIntent);

        //Sometimes the service stops too fast before the broadcast is sent,
        // so we reset it here for consistent ended state
        state = "";
        broadcastState(state);

        isShowingAd = false;
        adFailedCountdown = 0;

        Toast.makeText(activity, "Please check your internet and try again", Toast.LENGTH_SHORT).show();
    }
    private void preloadInterstitialAd() {
        if (interstitialAd == null) {

            AdRequest interstitialAdRequest = new AdRequest.Builder().build();//ca-app-pub-9799428944156340/2070618771
            InterstitialAd.load(activity, "ca-app-pub-9799", interstitialAdRequest, new InterstitialAdLoadCallback() {
                @Override
                public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                    super.onAdLoaded(interstitialAd);
                    PlayerManager.this.interstitialAd = interstitialAd;
                }

                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    super.onAdFailedToLoad(loadAdError);
                    PlayerManager.this.interstitialAd = null;
                    preloadInterstitialAd();
                }

            });
        }
    }

    private void broadcastState(String state) {
        eventIntent.putExtra(StreamService.EXTRA_STATE, state);
        activity.sendBroadcast(eventIntent);
    }
    private void checkInternet() {
        ConnectivityManager cm = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo nInfo = cm.getActiveNetworkInfo();
        boolean connected = nInfo != null && nInfo.isAvailable() && nInfo.isConnectedOrConnecting();
        if (!connected) {
            Toast.makeText(activity, "Check Internet", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupBroadcastReceiver() {
        IntentFilter eventFilter = new IntentFilter();
        eventFilter.addAction(StreamService.ACTION_EVENT_CHANGE);

        EventReceiver eventReceiver = new EventReceiver();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(eventReceiver, eventFilter, RECEIVER_NOT_EXPORTED);
        } else {
            activity.registerReceiver(eventReceiver, eventFilter);
        }
    }
    //Broadcast Receiver
    public class EventReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String state = intent.getStringExtra(StreamService.EXTRA_STATE);

            if (state.equals(StreamService.StreamStates.PREPARING) ||
                    state.equals(StreamService.StreamStates.PLAYING) ||
                    state.equals(StreamService.StreamStates.BUFFERING)) {
                // Do nothing
            } else if (state.equals(StreamService.StreamStates.IDLE) ||
                    state.equals(StreamService.StreamStates.ENDED)) {
                isPlaying = false;
            } else {
                // Unknown state
                isPlaying = false;
            }
        }
    }
}
