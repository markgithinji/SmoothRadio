package com.smoothradio.radio.feature.radio_list;

import static android.content.Context.RECEIVER_NOT_EXPORTED;

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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.smoothradio.radio.MainActivity;
import com.smoothradio.radio.R;
import com.smoothradio.radio.core.ui.RadioStationsHelper;
import com.smoothradio.radio.core.ui.RadioViewModel;
import com.smoothradio.radio.core.util.PlayerManager;
import com.smoothradio.radio.core.util.Resource;
import com.smoothradio.radio.databinding.FragmentMusicListBinding;
import com.smoothradio.radio.model.RadioStation;
import com.smoothradio.radio.service.StreamService;

import java.util.ArrayList;
import java.util.List;


public class RadioListFragment extends Fragment {
    RadioViewModel radioViewModel;
    private int stationId;
    RadioListRecyclerViewAdapter radioListRecyclerViewAdapter;
    MainActivity mainActivity;
    FragmentActivity activity;

    PlayerManager playerManager;
    RadioStation currentStation;

    FragmentMusicListBinding binding;

    public RadioListFragment() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        activity = (FragmentActivity) context;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mainActivity = (MainActivity) getContext();

        binding = FragmentMusicListBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        radioViewModel = new ViewModelProvider(this).get(RadioViewModel.class);

        playerManager = mainActivity.getPlayerManager();

        radioListRecyclerViewAdapter = mainActivity.getAdapter();

        setupObservers();

        setupBroadcastReceiver();

        setupRecyclerView();

        return root;
    }

    private void setupRecyclerView() {
        binding.rvList.setAdapter(radioListRecyclerViewAdapter);
        binding.rvList.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
        binding.rvList.setHasFixedSize(true);
        binding.rvList.addItemDecoration(new DividerItemDecoration(getContext(), 0));
        setupRecyclerViewScrollBehavior();
    }

    private void setupObservers() {
        // Init radio links
        radioViewModel.isFirstTime();
        radioViewModel.getStationId();
        radioViewModel.getRemoteLinks();

        radioViewModel.getIsFirstTimeLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.status == Resource.Status.SUCCESS) {
                Boolean isFirstTime = resource.data;
                if (Boolean.TRUE.equals(isFirstTime)) {
                    radioViewModel.createInitialLinks();
                    radioViewModel.saveIsFirstTime(false);
                }
            }
        });

        radioViewModel.getStationIdLivedata().observe(getViewLifecycleOwner(), stationId -> {
            this.stationId = stationId.data;
        });

        radioViewModel.getFavoriteStationNames().observe(getViewLifecycleOwner(), resource -> {
            if (resource.status == Resource.Status.SUCCESS) {
                radioListRecyclerViewAdapter.setFavouriteList(resource.data);
            }
        });
    }

    private void setupRecyclerViewScrollBehavior() {
        binding.rvList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                BottomSheetBehavior<View> bottomSheetBehavior = ((MainActivity) requireActivity()).bottomSheetBehavior;

                if (!recyclerView.canScrollVertically(1)) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                } else {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
            }
        });
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

    private void getLatestStationUsingSavedId() {
        currentStation = new RadioStation(0, "", "", "", "", stationId);

        int position = mainActivity.getAdapter().getPosOfStation(stationId);

        if (!(mainActivity.getAdapter().stationListIsEmpty() || position == RecyclerView.NO_POSITION)) {
            currentStation = mainActivity.getAdapter().getStationAtPosition(position);
        }
    }
    //Broadcast Receiver
    public class EventReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String state = intent.getStringExtra(StreamService.EXTRA_STATE);

//            radioListRecyclerViewAdapter.setState(state);
//            radioListRecyclerViewAdapter.updateStation(currentStation);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mainActivity == null) {
            mainActivity = (MainActivity) getContext();
        }

        Resource<Integer> intResource = radioViewModel.getStationIdLivedata().getValue();
        stationId = intResource.data;

        if(currentStation == null) getLatestStationUsingSavedId();

        //When we request for a ui state form service it doesn't work until the service starts playing. This is unusual/
        //unexpected behavior so we will manually set the state to preparing here for now.

        if (playerManager.getIsShowingAd()) {
//            broadcastState(StreamService.StreamStates.PREPARING);
        } else {
            Intent getStateFromServiceIntent = new Intent(StreamService.ACTION_GET_STATE).setPackage(activity.getPackageName());
            activity.sendBroadcast(getStateFromServiceIntent);//get ui state from service
        }
    }
}