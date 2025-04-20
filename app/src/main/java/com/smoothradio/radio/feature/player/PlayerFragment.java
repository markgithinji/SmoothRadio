package com.smoothradio.radio.feature.player;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.transition.TransitionManager;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.smoothradio.radio.MainActivity;
import com.smoothradio.radio.R;
import com.smoothradio.radio.databinding.FragmentPlayerBinding;
import com.smoothradio.radio.service.StreamService;
import com.smoothradio.radio.model.RadioStation;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;


public class PlayerFragment extends Fragment {
    MainActivity mainActivity;

    public RadioStation radioStation;

    FragmentPlayerBinding binding;
    //UI
    ImageView ivPlay;
    ImageView ivLargeLogo;
    TextView tvProgress;
    TextView tvStationName;
    TextView tvMetadata;

    public String state = "";
    Boolean info = false;
    String metadata = "";
    String newMetadata = "";
    public LottieAnimationView equalizerAnimation;
    SharedPreferences sharedPreferences;
    boolean isPlaying = false;
    public CoordinatorLayout coordinatorLayout;
    LinearLayout layout;
    //Ads
    InterstitialAd interstitialAd;

    static boolean isShowingAd;
    static int adFailedCountdown = 0;

    //For starting service
    Intent serviceIntent;
    Intent eventIntent;
    int stationId;
    FragmentActivity fragmentActivity;

    public PlayerFragment() {
    }


    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        fragmentActivity = (FragmentActivity) context;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainActivity = (MainActivity) getContext();

        //for ad ui updates
        eventIntent = new Intent(StreamService.ACTION_EVENT_CHANGE)
                .setPackage(fragmentActivity.getPackageName());


        registerBroadcasts();

        //intent for starting service
        serviceIntent = new Intent(fragmentActivity, StreamService.class);

        //SharedPrefs
        sharedPreferences = fragmentActivity.getSharedPreferences("PlayerFragmentSharedPref", Context.MODE_PRIVATE);//////////for repository
    }
    private void registerBroadcasts() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            fragmentActivity.registerReceiver(new EventReceiver(),
                    new IntentFilter(StreamService.ACTION_EVENT_CHANGE), Context.RECEIVER_NOT_EXPORTED);
            fragmentActivity.registerReceiver(new UpdateUIReceiver(),
                    new IntentFilter(StreamService.ACTION_UPDATE_UI), Context.RECEIVER_NOT_EXPORTED);
            fragmentActivity.registerReceiver(new MetadataReceiver(),
                    new IntentFilter(StreamService.ACTION_METADATA_CHANGE), Context.RECEIVER_NOT_EXPORTED);
        } else {
            fragmentActivity.registerReceiver(new EventReceiver(), new IntentFilter(StreamService.ACTION_EVENT_CHANGE));
            fragmentActivity.registerReceiver(new UpdateUIReceiver(), new IntentFilter(StreamService.ACTION_UPDATE_UI));
            fragmentActivity.registerReceiver(new MetadataReceiver(), new IntentFilter(StreamService.ACTION_METADATA_CHANGE));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mainActivity == null) {
            mainActivity = (MainActivity) getContext();
        }

        stationId = sharedPreferences.getInt("stationId", 0);///////////for repo

        radioStation = new RadioStation(0, "", "", "", "", stationId);
        int position = mainActivity.radioStationsList.indexOf(radioStation);

        radioStation = mainActivity.radioListRecyclerViewAdapter.stationListCopy.get(position);

        binding.ivLargeLogo.setImageResource(radioStation.getSmallLogo());
        binding.tvStationNamePlayerFrag.setText(radioStation.getStationName());

        //only update if were had started loading ad and we are also playing. If we arent playing, user will see 'preparing audio' when we
        //arent actually doing anything esp after stopping service while loading ad
        state = "";
        broadcastState(state);
        if (isShowingAd) {
            state = StreamService.StreamStates.PREPARING;
            broadcastState(state);
        } else {
            Intent getStateFromServiceIntent = new Intent(StreamService.ACTION_GET_STATE).setPackage(fragmentActivity.getPackageName());
            fragmentActivity.sendBroadcast(getStateFromServiceIntent);//get ui state from service
        }
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentPlayerBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        //Banner AD
        AdRequest playerFragAdRequest = new AdRequest.Builder().build();
        binding.adView.loadAd(playerFragAdRequest);

        //equalizerAnimation
        binding.equalizerAnimation.playAnimation();
        binding.equalizerAnimation.setVisibility(View.INVISIBLE);

        //Adding OnclickListeners
        binding.ivPlayButton.setOnClickListener(new PlayButtonListener());
        binding.ivRefresh.setOnClickListener(new Refresh());
        binding.ivSetTimer.setOnClickListener(new SetTimerOnclickListener());


        layout = binding.playerFrag;
        binding.lottieLoadingAnimation.setVisibility(View.INVISIBLE);

        return root;
    }

    public class UpdateUIReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            state = intent.getStringExtra(StreamService.EXTRA_STATE);
            if(state!=null)updateUI();
        }
    }

    /**
     *  `MetadataReceiver` is a `BroadcastReceiver` responsible for receiving and displaying metadata
     *  information about the currently playing audio stream.  It listens for broadcasts from
     *  `StreamService` containing the title of the current stream and updates the UI accordingly.
     *
     *  Specifically, it receives an intent with the extra `StreamService.EXTRA_TITLE`, extracts the
     *  title string, truncates it to a maximum of 70 characters, and updates a `TextView` with the
     *  truncated title.  It also uses a `TransitionManager` to animate the UI update for a smoother
     *  user experience.
     */
    public class MetadataReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            metadata = intent.getStringExtra(StreamService.EXTRA_TITLE);
            if (metadata != null) {
                newMetadata = metadata.substring(0, Math.min(metadata.length(), 70));
                tvMetadata.setText(newMetadata);
                TransitionManager.beginDelayedTransition(layout);
            }
        }
    }

    //from MainActivity.........
    public void playFromMainActivity(RadioStation radioStation) {
        this.radioStation = radioStation;

        ivLargeLogo.setImageResource(radioStation.getSmallLogo());
        tvStationName.setText(radioStation.getStationName());

        //update ui loading ad pre-play
        state = StreamService.StreamStates.PREPARING;
        broadcastState(state);

        isPlaying = true;


        if (!isShowingAd) {//avoid multiple clicks while loading ad

            serviceIntent.setAction(StreamService.ACTION_SHOW_AD);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                fragmentActivity.startForegroundService(serviceIntent);
            } else {
                fragmentActivity.startService(serviceIntent);
            }

            //ads
            loadInterstitialAd();
            checkInternet();
            //Analytics
            mainActivity.sendFirebaseAnalytics(radioStation.getStationName());
        } else {//if ad already loaded, show it; otherwise nothing will happen so continue waiting for ad to finish loading
//            showAdWithoutReloading();
        }
    }

    //Broadcast Receiver
    public class EventReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            state = intent.getStringExtra(StreamService.EXTRA_STATE);
            updateUI();
        }
    }

    public class Refresh implements View.OnClickListener {

        @Override
        public void onClick(View view) {
            //update ui pre-play
            state = StreamService.StreamStates.PREPARING;
            broadcastState(state);
            isPlaying = true;

            if (!isShowingAd) {//avoid multiple clicks while loading ad
                if (isPlaying)// stop from within service is faster than stopping the whole service as a whole
                {
                    serviceIntent.setAction(StreamService.ACTION_SHOW_AD);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        fragmentActivity.startForegroundService(serviceIntent);
                    } else {
                        fragmentActivity.startService(serviceIntent);
                    }

                }

                loadInterstitialAd();
                checkInternet();
            } else {//if ad already loaded, show it; otherwise nothing will happen so continue waiting for ad to finish loading
//                showAdWithoutReloading();
            }
            Toast.makeText(fragmentActivity, "refreshed!", Toast.LENGTH_SHORT).show();
        }
    }

    public class PlayButtonListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            playOrStop();
        }
    }

    public void playOrStop() {
        if (isPlaying) {
            fragmentActivity.stopService(serviceIntent);
        } else {
            //update ui pre-play
            state = StreamService.StreamStates.PREPARING;
            broadcastState(state);
            isPlaying = true;

            if (!isShowingAd)// prevent multiple clicks while loading ads
            {
                loadInterstitialAd();
                checkInternet();
                //Analytics
                mainActivity.sendFirebaseAnalytics(radioStation.getStationName());
            } else {//if ad already loaded, show it; otherwise nothing will happen so continue waiting for ad to finish loading
//                showAdWithoutReloading();
            }
        }
    }

    class SetTimerOnclickListener implements View.OnClickListener {
        Intent setTimerIntent = new Intent();
        Calendar calendar;
        String time1;
        String time2;

        @Override
        public void onClick(View view) {
            calendar = Calendar.getInstance();
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            int minute = calendar.get(Calendar.MINUTE);
            time1 = java.text.DateFormat.getTimeInstance().format(calendar.getTime());
            MaterialTimePicker picker = new MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_12H)
                    .setHour(hour)
                    .setMinute(minute)
                    .setTitleText("Set Time To Turn Off Radio")
                    .build();
            picker.show(requireActivity().getSupportFragmentManager(), "SetTimerFrag");
            picker.addOnPositiveButtonClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int i = picker.getHour();
                    int i1 = picker.getMinute();
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        calendar.set(Calendar.HOUR_OF_DAY, i);
                        calendar.set(Calendar.MINUTE, i1);
                        calendar.set(Calendar.SECOND, 0);
                        Long timeInMillis = calendar.getTimeInMillis();
                        setTimerIntent.putExtra(StreamService.EXTRA_TIME_IN_MILLIS, timeInMillis);
                        setTimerIntent.setAction(StreamService.ACTION_SET_TIMER);
                        setTimerIntent.setPackage(fragmentActivity.getPackageName());
                        fragmentActivity.sendBroadcast(setTimerIntent);

                        time2 = java.text.DateFormat.getTimeInstance().format(calendar.getTime());
                        SimpleDateFormat simpleDateFormat
                                = new SimpleDateFormat("hh:mm:ss aa", Locale.getDefault());
                        // Parsing the Time Period
                        Date date1 = null;
                        Date date2 = null;
                        try {
                            date2 = simpleDateFormat.parse(time1);
                            date1 = simpleDateFormat.parse(time2);
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
                        // Calculating the difference in milliseconds
                        if (date1 != null || date2 != null) {
                            long differenceInMilliSeconds = Math.abs(date2.getTime() - date1.getTime());
                            // Calculating the difference in Hours
                            long differenceInHours = TimeUnit.MILLISECONDS.toHours(differenceInMilliSeconds);//(differenceInMilliSeconds / (60 * 60 * 1000)) % 24;
                            // Calculating the difference in Minutes
                            long differenceInMinutes = TimeUnit.MILLISECONDS.toMinutes(differenceInMilliSeconds) % 60;//(differenceInMilliSeconds / (60 * 1000)) % 60;
                            // Calculating the difference in Seconds
                            long differenceInSeconds = TimeUnit.MILLISECONDS.toSeconds(differenceInMilliSeconds) % 60;//differenceInMilliSeconds / 1000) % 60;
                            // Printing the answer
                            Snackbar.make(coordinatorLayout, "Radio will Stop after " + differenceInHours + " Hours "
                                    + differenceInMinutes + " Minutes "
                                    + differenceInSeconds + " Seconds.", Snackbar.LENGTH_LONG).show();
                        }

                    } else {
                        Toast.makeText(mainActivity, "Sorry. Unsupported Android Version", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }

    void playOnly() {
        serviceIntent.setAction(StreamService.ACTION_START);
        serviceIntent.putExtra(StreamService.EXTRA_LINK, radioStation.getUrl());
        serviceIntent.putExtra(StreamService.EXTRA_LOGO, radioStation.getSmallLogo());
        serviceIntent.putExtra(StreamService.EXTRA_STATION_NAME, radioStation.getStationName());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            fragmentActivity.startForegroundService(serviceIntent);
        } else {
            fragmentActivity.startService(serviceIntent);
        }
        binding.ivLargeLogo.setImageResource(radioStation.getSmallLogo());
        binding.tvStationNamePlayerFrag.setText(radioStation.getStationName());

        //update ui pre-play
        state = StreamService.StreamStates.PREPARING;
        broadcastState(state);

        isPlaying = true;

        loadInterstitialAdWithoutPlay();
    }

    void updateUI() {
        TransitionManager.beginDelayedTransition(layout);

        if (state.equals(StreamService.StreamStates.PREPARING)) {
            binding.tvProgress.setText(state);
            binding.tvMetadata.setText("");
            binding.lottieLoadingAnimation.setVisibility(View.VISIBLE);
            binding.equalizerAnimation.setVisibility(View.INVISIBLE);
            updateList();
            isPlaying = true;

        } else if (state.equals(StreamService.StreamStates.IDLE)) {
            binding.tvProgress.setText("");
            binding.tvMetadata.setText("");
            binding.lottieLoadingAnimation.setVisibility(View.INVISIBLE);
            binding.equalizerAnimation.setVisibility(View.INVISIBLE);
            if (info) {
                binding.ivPlayButton.setImageResource(R.drawable.playerfragplayicon);
                info = false;
            } else {
                binding.ivPlayButton.setImageResource(R.drawable.playerfragplayicon);
            }

            isPlaying = true;
            updateList();
            isPlaying = false;
        } else if (state.equals(StreamService.StreamStates.PLAYING)) {
            binding.tvProgress.setText(state);
            binding.lottieLoadingAnimation.setVisibility(View.INVISIBLE);
            binding.equalizerAnimation.setVisibility(View.VISIBLE);
            if (info) {
                binding.ivPlayButton.setImageResource(R.drawable.playerfragpauseicon);
                info = false;
            } else {
                binding.ivPlayButton.setImageResource(R.drawable.playerfragpauseicon);
            }

            isPlaying = true;

            updateList();

        } else if (state.equals(StreamService.StreamStates.IDLE)) {
            binding.tvProgress.setText(state);
            binding.tvMetadata.setText("");
            binding.lottieLoadingAnimation.setVisibility(View.INVISIBLE);
            binding.equalizerAnimation.setVisibility(View.INVISIBLE);
            binding.ivPlayButton.setImageResource(R.drawable.playerfragplayicon);

            isPlaying = true;
            updateList();
            isPlaying = false;

        } else if (state.equals(StreamService.StreamStates.BUFFERING)) {
            binding.tvProgress.setText(state);
            binding.lottieLoadingAnimation.setVisibility(View.VISIBLE);
            binding.equalizerAnimation.setVisibility(View.INVISIBLE);
            if (info) {
                binding.ivPlayButton.setImageResource(R.drawable.playerfragplayicon);
                info = false;
            } else {
                binding.ivPlayButton.setImageResource(R.drawable.playerfragplayicon);
            }
            isPlaying = true;
            updateList();
        } else {
            state = "";
            binding.tvProgress.setText(state);
            binding.tvMetadata.setText("");
            binding.lottieLoadingAnimation.setVisibility(View.INVISIBLE);
            binding.equalizerAnimation.setVisibility(View.INVISIBLE);
            if (info) {

                binding.ivPlayButton.setImageResource(R.drawable.playerfragplayicon);
                info = false;
            } else {
                binding.ivPlayButton.setImageResource(R.drawable.playerfragplayicon);
            }
            updateList();
            isPlaying = false;
        }

    }

    void updateList() {
        if (mainActivity != null && mainActivity.radioListRecyclerViewAdapter != null) {
            mainActivity.radioListRecyclerViewAdapter.updateStationList();
        }
    }

    void loadInterstitialAd() {
        isShowingAd = true;
        if (interstitialAd == null) {
            AdRequest interstitialAdRequest = new AdRequest.Builder().build();
            InterstitialAd.load(fragmentActivity, "ca-app-pub-9799428944156340/4028560879", interstitialAdRequest, new InterstitialAdLoadCallback() {
                @Override
                public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                    super.onAdLoaded(interstitialAd);
                    PlayerFragment.this.interstitialAd = interstitialAd;
                    if (isPlaying)//only show ad if user has pressed play and hasn't stopped the service manually.
                    {
                        showAd();
                    }
                }

                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    super.onAdFailedToLoad(loadAdError);
                    PlayerFragment.this.interstitialAd = null;
                    if (loadAdError.getCode() == 1 || loadAdError.getCode() == 3) {// only play when user has seen enough ads or no ad fill
                        playOnly();
                        isShowingAd = false;
                        adFailedCountdown = 0;
                    } else {
                        countdownAdFailed();
                    }
                }

            });
        } else {
            if (isPlaying)//only show ad if user has pressed play and hasn't stopped the service manually.
            {
                showAd();
            }
        }
    }

    void showAd() {
        if (interstitialAd != null) {
            isShowingAd = true;
            interstitialAd.show(fragmentActivity);
            PlayerFragment.this.interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {

                @Override
                public void onAdDismissedFullScreenContent() {

                    PlayerFragment.this.interstitialAd = null;

                    playOnly();
                    isShowingAd = false;
                    loadInterstitialAdWithoutPlay();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {

                    PlayerFragment.this.interstitialAd = null;
                    isShowingAd = false;
                    fragmentActivity.stopService(serviceIntent);
//                    loadInterstitialAd();
                }
            });
        } else {
            loadInterstitialAd();
        }
    }

    void loadInterstitialAdWithoutPlay() {
        if (interstitialAd == null) {

            AdRequest interstitialAdRequest = new AdRequest.Builder().build();
            InterstitialAd.load(fragmentActivity, "ca-app-pub-9799428944156340/4028560879", interstitialAdRequest, new InterstitialAdLoadCallback() {
                @Override
                public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                    super.onAdLoaded(interstitialAd);
                    PlayerFragment.this.interstitialAd = interstitialAd;
                }

                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    super.onAdFailedToLoad(loadAdError);
                    PlayerFragment.this.interstitialAd = null;
                    loadInterstitialAdWithoutPlay();
                }

            });
        }
    }

//    void showAdWithoutReloading() {
//        if (interstitialAd != null) {
//            isShowingAd = true;
//            interstitialAd.show(mainActivity);
//            PlayerFragment.this.interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
//                @Override
//                public void onAdDismissedFullScreenContent() {
//                    PlayerFragment.this.interstitialAd = null;
//
//                    playOnly();
//                    isShowingAd = false;
//                    loadInterstitialAdWithoutPlay();
//                }
//
//                @Override
//                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
//                    PlayerFragment.this.interstitialAd = null;
//                    isShowingAd = false;
//                }
//            });
//        } else {
//            loadInterstitialAdWithoutPlay();
//        }
//    }

    void countdownAdFailed()// counts no of times ad failed to load so as to stop trying to load a new ad
    {
        adFailedCountdown++;
        if (adFailedCountdown < 2) {
            loadInterstitialAd();
        } else {
            //update ui
            fragmentActivity.stopService(serviceIntent);

            state = "";
            broadcastState(state);

            isPlaying = false;
            Toast.makeText(fragmentActivity, "Please check your internet and try again", Toast.LENGTH_SHORT).show();

            adFailedCountdown = 0;
            isShowingAd = false;// allow user to click button to attempt to start play again
        }
    }
    private void broadcastState(String state) {
        eventIntent.putExtra(StreamService.EXTRA_STATE, state);
        fragmentActivity.sendBroadcast(eventIntent);
    }

    void checkInternet() {
        ConnectivityManager cm = (ConnectivityManager) fragmentActivity.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo nInfo = cm.getActiveNetworkInfo();
        boolean connected = nInfo != null && nInfo.isAvailable() && nInfo.isConnectedOrConnecting();
        if (!connected) {
            Toast.makeText(fragmentActivity, "Check Internet", Toast.LENGTH_SHORT).show();
        }
    }
    public boolean getIsPlaying() {
        return isPlaying;
    }

}


