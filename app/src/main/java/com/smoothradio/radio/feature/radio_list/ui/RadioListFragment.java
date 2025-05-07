package com.smoothradio.radio.feature.radio_list.ui;

import static android.content.Context.RECEIVER_NOT_EXPORTED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.smoothradio.radio.MainActivity;
import com.smoothradio.radio.core.model.ListItem;
import com.smoothradio.radio.core.ui.RadioViewModel;
import com.smoothradio.radio.core.util.PlayerManager;
import com.smoothradio.radio.core.util.Resource;
import com.smoothradio.radio.databinding.FragmentMusicListBinding;
import com.smoothradio.radio.feature.radio_list.ui.adapter.RadioListRecyclerViewAdapter;
import com.smoothradio.radio.core.model.RadioStation;
import com.smoothradio.radio.feature.radio_list.util.RadioStationsHelper;
import com.smoothradio.radio.service.StreamService;

import java.util.ArrayList;
import java.util.List;


public class RadioListFragment extends Fragment{
    private FragmentMusicListBinding binding;
    private RadioViewModel radioViewModel;
    private RadioListRecyclerViewAdapter radioListRecyclerViewAdapter;
    private PlayerManager playerManager;
    private final BroadcastReceiver eventReceiver = new EventReceiver();
    private Intent eventIntent;
    private int lastStationId = -1;
    private RadioStation currentStation;
    private MainActivity mainActivity;
    private FragmentActivity fragmentActivity;

    public RadioListFragment() {
        // Required empty public constructor
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

        setupBroadcastReceiver();
        initializeComponents();
    }
    private void initializeComponents() {
        radioViewModel = new ViewModelProvider(fragmentActivity).get(RadioViewModel.class);
        playerManager = mainActivity.getPlayerManager();
        eventIntent = new Intent(StreamService.ACTION_EVENT_CHANGE)
                .setPackage(fragmentActivity.getPackageName());
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        binding = FragmentMusicListBinding.inflate(inflater, container, false);

        radioListRecyclerViewAdapter = mainActivity.getAdapter();

        setupObservers();

        setupRecyclerView();

        return binding.getRoot();
    }

    private void setupRecyclerView() {
        binding.rvRadioList.setAdapter(radioListRecyclerViewAdapter);
        binding.rvRadioList.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
        binding.rvRadioList.setHasFixedSize(true);
        binding.rvRadioList.addItemDecoration(new DividerItemDecoration(getContext(), 0));
        setupRecyclerViewScrollBehavior();
    }

    private void setupRecyclerViewScrollBehavior() {
        binding.rvRadioList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                BottomSheetBehavior<View> bottomSheetBehavior = mainActivity.bottomSheetBehavior;

                if (!recyclerView.canScrollVertically(1)) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                } else {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
            }
        });
    }
    private void setupObservers() {
        // Init radio links
//        radioViewModel.getStationId();
//
//        radioViewModel.getStationIdLivedata().observe(getViewLifecycleOwner(), intResource -> {
//            if (intResource.status == Resource.Status.SUCCESS) {
//                lastStationId = intResource.data;
//            }
//        });
        radioViewModel.getPlayingStation().observe(getViewLifecycleOwner(), radioStation -> {
            if (radioStation != null) {
                lastStationId = radioStation.getId();
            }
        });

        radioViewModel.getRemoteLinksLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                List<String> newLinks = resource.data;
                List<RadioStation> localStations = radioViewModel.getAllStations().getValue();

                List<RadioStation> newStations = RadioStationsHelper.createRadioStations(newLinks, localStations);

                radioViewModel.insertStations(newStations);
            }
        });

        radioViewModel.getAllStations().observe(getViewLifecycleOwner(), stations -> {
            radioListRecyclerViewAdapter.update(stations);
            radioViewModel.onRemoteLinksLoaded();
        });

        radioViewModel.getSelectedStation().observe(getViewLifecycleOwner(), radioStation -> {
            this.currentStation = radioStation;
            playerManager.setRadioStation(radioStation);

            if (lastStationId == radioStation.getId()) {
                playerManager.playOrStop();
            } else {
                playerManager.playFromMainActivity();
            }
            radioViewModel.savePlayingStationId(radioStation.getId());
        });

        radioViewModel.getFavoriteStations().observe(getViewLifecycleOwner(), favorites -> {
            radioListRecyclerViewAdapter.setFavouriteStations(favorites);
        });

    }

    private void setupBroadcastReceiver() {
        IntentFilter eventFilter = new IntentFilter();
        eventFilter.addAction(StreamService.ACTION_EVENT_CHANGE);

        EventReceiver eventReceiver = new EventReceiver();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            fragmentActivity.registerReceiver(eventReceiver, eventFilter, RECEIVER_NOT_EXPORTED);
        } else {
            fragmentActivity.registerReceiver(eventReceiver, eventFilter);
        }
    }


    public class EventReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String state = intent.getStringExtra(StreamService.EXTRA_STATE);

            radioListRecyclerViewAdapter.setState(state);
            radioListRecyclerViewAdapter.updateStation(currentStation);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mainActivity == null) {
            mainActivity = (MainActivity) getContext();
        }

        if(currentStation == null) currentStation  = mainActivity.getStationUsingSavedId();

        // Manually trigger adapter update on resume

        if (currentStation != null) {
            radioListRecyclerViewAdapter.setPlayingStation(currentStation.getId());//update playing station
        }
        if (playerManager.getIsShowingAd()) {
            broadcastState(StreamService.StreamStates.PREPARING);
        } else {
            Intent getStateFromServiceIntent = new Intent(StreamService.ACTION_GET_STATE).setPackage(fragmentActivity.getPackageName());
            fragmentActivity.sendBroadcast(getStateFromServiceIntent);//update ui state from service
        }
    }
    private void broadcastState(String state) {
        eventIntent.putExtra(StreamService.EXTRA_STATE, state);
        fragmentActivity.sendBroadcast(eventIntent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        fragmentActivity.unregisterReceiver(eventReceiver);
    }

}