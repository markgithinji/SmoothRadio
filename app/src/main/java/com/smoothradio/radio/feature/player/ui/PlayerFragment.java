package com.smoothradio.radio.feature.player.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.TransitionManager;

import com.google.android.gms.ads.AdRequest;
import com.smoothradio.radio.MainActivity;
import com.smoothradio.radio.R;
import com.smoothradio.radio.core.ui.RadioViewModel;
import com.smoothradio.radio.core.util.PlayerManager;
import com.smoothradio.radio.core.util.Resource;
import com.smoothradio.radio.feature.player.util.TimerSetterHelper;
import com.smoothradio.radio.databinding.FragmentPlayerBinding;
import com.smoothradio.radio.core.model.RadioStation;
import com.smoothradio.radio.service.StreamService;


public class PlayerFragment extends Fragment {
    MainActivity mainActivity;

    public RadioStation currentStation;

    FragmentPlayerBinding binding;

    public String state = "";
    public CoordinatorLayout coordinatorLayout;


    //For starting service
    Intent eventIntent;
    int stationId;
    FragmentActivity fragmentActivity;
    RadioViewModel radioViewModel;

    PlayerManager playerManager;

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

        radioViewModel = new ViewModelProvider(fragmentActivity).get(RadioViewModel.class);

        playerManager = mainActivity.getPlayerManager();

        registerBroadcasts();

        //for player state ui updates
        eventIntent = new Intent(StreamService.ACTION_EVENT_CHANGE)
                .setPackage(fragmentActivity.getPackageName());
    }

    private void setupObserver() {
        radioViewModel.getStationIdLivedata().observe(getViewLifecycleOwner(), intResource -> {
            Log.d("PlayerFragment","getStationIdLivedata");
            if (intResource.status == Resource.Status.SUCCESS) {
                stationId = intResource.data;

                if (currentStation == null) getLatestStationUsingSavedId();

                binding.ivLargeLogo.setImageResource(currentStation.getLogoResource());
                binding.tvStationNamePlayerFrag.setText(currentStation.getStationName());
            }
        });

        radioViewModel.getSelectedStation().observe(getViewLifecycleOwner(), station -> {
            currentStation = station;
        });
    }

    private void getLatestStationUsingSavedId() {
        currentStation = new RadioStation(stationId,0, "", "", "", "",true,false );

        int position = mainActivity.getAdapter().getPositionOfStation(stationId);

        if (!(mainActivity.getAdapter().listIsEmpty() || position == RecyclerView.NO_POSITION)) {
            currentStation = mainActivity.getAdapter().getStationAtPosition(position);
        }
    }

    private void registerBroadcasts() {
        IntentFilter eventFilter = new IntentFilter();
        eventFilter.addAction(StreamService.ACTION_EVENT_CHANGE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            fragmentActivity.registerReceiver(new EventReceiver(), eventFilter, Context.RECEIVER_NOT_EXPORTED);
            fragmentActivity.registerReceiver(new MetadataReceiver(),
                    new IntentFilter(StreamService.ACTION_METADATA_CHANGE), Context.RECEIVER_NOT_EXPORTED);
        } else {
            fragmentActivity.registerReceiver(new EventReceiver(), eventFilter);
            fragmentActivity.registerReceiver(new MetadataReceiver(), new IntentFilter(StreamService.ACTION_METADATA_CHANGE));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mainActivity == null) {
            mainActivity = (MainActivity) getContext();
        }
        radioViewModel.reloadStationId();

        //When we request for a ui state form service it doesn't work until the service starts playing. This is unusual/
        //unexpected behavior so we will manually set the state to preparing here for now.
        if (playerManager.getIsShowingAd()) {
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

        setUpUI();

        radioViewModel.getStationId();
        setupObserver();

        return root;
    }

    private void setUpUI() {
        //Banner AD
        AdRequest playerFragAdRequest = new AdRequest.Builder().build();
        binding.adView.loadAd(playerFragAdRequest);

        //Adding OnclickListeners
        binding.ivPlayButton.setOnClickListener(new PlayButtonListener());
        binding.ivRefresh.setOnClickListener(new Refresh());
        binding.ivSetTimer.setOnClickListener(new SetTimerOnclickListener());
    }

    /**
     * `MetadataReceiver` is a `BroadcastReceiver` responsible for receiving and displaying metadata
     * information about the currently playing audio stream.  It listens for broadcasts from
     * `StreamService` containing the title of the current stream and updates the UI accordingly.
     * <p>
     * Specifically, it receives an intent with the extra `StreamService.EXTRA_TITLE`, extracts the
     * title string, truncates it to a maximum of 70 characters, and updates a `TextView` with the
     * truncated title.  It also uses a `TransitionManager` to animate the UI update for a smoother
     * user experience.
     */
    public class MetadataReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String metadata = intent.getStringExtra(StreamService.EXTRA_TITLE);
            if (metadata != null) {
                String truncatedMetadata = metadata.substring(0, Math.min(metadata.length(), 70));
                binding.tvMetadata.setText(truncatedMetadata);
                TransitionManager.beginDelayedTransition(binding.playerFrag);
            }
        }
    }


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
            playerManager.refresh();
        }
    }

    public class PlayButtonListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            radioViewModel.setSelectedStation(currentStation);
            playerManager.playOrStop();
        }
    }

    class SetTimerOnclickListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            new TimerSetterHelper(fragmentActivity,coordinatorLayout);
        }
    }

    void updateUI() {
        TransitionManager.beginDelayedTransition(binding.playerFrag);

        binding.tvProgress.setText(state);
        binding.lottieLoadingAnimation.setVisibility(View.INVISIBLE);
        binding.equalizerAnimation.setVisibility(View.INVISIBLE);
        binding.tvMetadata.setText("");
        binding.ivPlayButton.setImageResource(R.drawable.playerfragplayicon);

        if (StreamService.StreamStates.PREPARING.equals(state)) {
            binding.lottieLoadingAnimation.setVisibility(View.VISIBLE);
            return;
        }

        if (StreamService.StreamStates.PLAYING.equals(state)) {
            binding.equalizerAnimation.setVisibility(View.VISIBLE);
            binding.ivPlayButton.setImageResource(R.drawable.playerfragpauseicon);
            return;
        }

        if (StreamService.StreamStates.BUFFERING.equals(state)) {
            binding.lottieLoadingAnimation.setVisibility(View.VISIBLE);
            return;
        }

        if (StreamService.StreamStates.ENDED.equals(state) ||
                StreamService.StreamStates.IDLE.equals(state)) {
            return;
        }

        // If unknown state
        binding.tvProgress.setText("");
        binding.tvMetadata.setText("");
    }

    private void broadcastState(String state) {
        eventIntent.putExtra(StreamService.EXTRA_STATE, state);
        fragmentActivity.sendBroadcast(eventIntent);
    }
}


