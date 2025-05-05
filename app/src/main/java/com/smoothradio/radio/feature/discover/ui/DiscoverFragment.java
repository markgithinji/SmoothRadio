package com.smoothradio.radio.feature.discover.ui;

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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.smoothradio.radio.MainActivity;
import com.smoothradio.radio.feature.radio_list.util.RadioStationsHelper;
import com.smoothradio.radio.core.ui.RadioViewModel;
import com.smoothradio.radio.core.util.Resource;
import com.smoothradio.radio.databinding.FragmentDiscoverBinding;
import com.smoothradio.radio.feature.discover.ui.adapter.CategoryRecyclerViewAdapter;
import com.smoothradio.radio.feature.discover.ui.adapter.DiscoverRecyclerViewAdapter;
import com.smoothradio.radio.feature.discover.util.CategoryHelper;
import com.smoothradio.radio.core.model.Category;
import com.smoothradio.radio.core.model.RadioStation;
import com.smoothradio.radio.service.StreamService;

import java.util.ArrayList;
import java.util.List;


public class DiscoverFragment extends Fragment {

    private FragmentDiscoverBinding binding;
    private DiscoverRecyclerViewAdapter discoverRecyclerViewAdapter;
    private RadioViewModel radioViewModel;

    private FragmentActivity fragmentActivity;
    private MainActivity mainActivity;
    private EventReceiver eventReceiver;
    private RadioStation currentStation;

    public DiscoverFragment() {

    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        fragmentActivity = (FragmentActivity) context;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainActivity = (MainActivity) getActivity();
        setupBroadcastReceiver();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentDiscoverBinding.inflate(inflater, container, false);

        discoverRecyclerViewAdapter = new DiscoverRecyclerViewAdapter(new ArrayList<>());
        radioViewModel = new ViewModelProvider(fragmentActivity).get(RadioViewModel.class);

        setupObservers();
        setupRecyclerView();

        RadioStationActionHandler radioStationActionHandler = new RadioStationActionHandler(radioViewModel);
        discoverRecyclerViewAdapter.setRadioStationActionListener(radioStationActionHandler);

        return binding.getRoot();
    }

    private void setupObservers() {
        // Init radio links
        radioViewModel.getStationId();
        radioViewModel.getRemoteLinks();


        radioViewModel.getRemoteLinksLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.status == Resource.Status.SUCCESS) {
                List<RadioStation> radioStations = RadioStationsHelper.createRadioStations(resource.data);
                List<Category> categoryList = CategoryHelper.createCategories(radioStations);
                discoverRecyclerViewAdapter.updateCategoryList(categoryList);
            }
        });

        radioViewModel.getSelectedStation().observe(getViewLifecycleOwner(), radioStation -> {
            this.currentStation = radioStation;
            mainActivity.getPlayerManager().setRadioStation(radioStation);
            discoverRecyclerViewAdapter.setSelectedStation(radioStation);
            radioViewModel.saveStationId(radioStation.getId());
        });

        radioViewModel.getFavoriteStationNames().observe(getViewLifecycleOwner(), resource -> {
            if (resource.status == Resource.Status.SUCCESS) {
//                radioListRecyclerViewAdapter.setFavouriteList(resource.data);
            }
        });
    }

    private void setupRecyclerView() {
        binding.rvDiscover.setAdapter(discoverRecyclerViewAdapter);
        binding.rvDiscover.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.VERTICAL, false));
        binding.rvDiscover.setHasFixedSize(true);
    }

    private void setupBroadcastReceiver() {
        eventReceiver = new EventReceiver();
        IntentFilter eventFilter = new IntentFilter();
        eventFilter.addAction(StreamService.ACTION_EVENT_CHANGE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            fragmentActivity.registerReceiver(eventReceiver, eventFilter, RECEIVER_NOT_EXPORTED);
        } else {
            fragmentActivity.registerReceiver(eventReceiver, eventFilter);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        fragmentActivity.unregisterReceiver(eventReceiver);
    }

    public class EventReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String state = intent.getStringExtra(StreamService.EXTRA_STATE);

            discoverRecyclerViewAdapter.updateStationInPlayingCategory(currentStation,state);
        }
    }


    public class RadioStationActionHandler implements CategoryRecyclerViewAdapter.RadioStationActionListener {
        private final RadioViewModel radioViewModel;

        public RadioStationActionHandler(RadioViewModel radioViewModel) {
            this.radioViewModel = radioViewModel;
        }

        @Override
        public void onStationSelected(RadioStation station) {
            radioViewModel.setSelectedStation(station);
        }

        @Override
        public void onAddToFavorites(String stationName) {
            radioViewModel.addToFavorites(stationName);
        }

        @Override
        public void onRemoveFromFavorites(String stationName) {
            radioViewModel.removeFromFavorites(stationName);
        }

        @Override
        public void onRequestshowToast(String message) {
            showToast(message);
        }

        private void showToast(String message) {
            Toast.makeText(fragmentActivity, message, Toast.LENGTH_SHORT).show();
        }
    }
}