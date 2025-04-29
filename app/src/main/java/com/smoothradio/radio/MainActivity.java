package com.smoothradio.radio;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.smoothradio.radio.core.ui.RadioStationsHelper;
import com.smoothradio.radio.core.ui.RadioViewModel;
import com.smoothradio.radio.core.ui.ViewPagerAdapter;
import com.smoothradio.radio.core.util.CacheUtil;
import com.smoothradio.radio.core.util.ConsentHelper;
import com.smoothradio.radio.core.util.PlayerManager;
import com.smoothradio.radio.core.util.Resource;
import com.smoothradio.radio.databinding.ActivityMainBinding;
import com.smoothradio.radio.feature.about.AboutFragment;
import com.smoothradio.radio.feature.radio_list.RadioListRecyclerViewAdapter;
import com.smoothradio.radio.model.RadioStation;
import com.smoothradio.radio.service.StreamService;
import com.smoothradio.radio.util.SortDialog;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // View binding
    public ActivityMainBinding binding;

    // ViewPager
    private ViewPagerAdapter viewPagerAdapter;
    private int tabPosition;

    // ViewModel
    public RadioViewModel radioViewModel;

    // Bottom sheet
    public BottomSheetBehavior<View> bottomSheetBehavior;

    // Input & keyboard
    private InputMethodManager inputMethodManager;
    private boolean searchVisible = false;

    // Firebase
    private FirebaseAnalytics firebaseAnalytics;
    public RadioListRecyclerViewAdapter radioListRecyclerViewAdapter;

    PlayerManager playerManager;

    private int lastStationId;

    RadioStation currentPlayingStation;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SplashScreen.installSplashScreen(this);// for displaying splash screen

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        new ConsentHelper(this).showConsentForm();

        radioViewModel = new ViewModelProvider(this).get(RadioViewModel.class);

        bottomSheetBehavior = BottomSheetBehavior.from(binding.miniPlayer.bottomSheetLayout);

        playerManager = new PlayerManager(this);

        radioListRecyclerViewAdapter = new RadioListRecyclerViewAdapter(new ArrayList<>());

        setupObservers();

        setupViewPagerAndTabs();

        setupSearchUI();

        binding.miniPlayer.ivPlayMiniPlayerLayout.setOnClickListener(v -> miniPlayerPlayPause());

        setupBroadcastReceiver();

        requestPermissions();

        inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);//for Hiding KeyBoard

        firebaseAnalytics = FirebaseAnalytics.getInstance(this);
    }

    private void setupObservers() {
        // Init radio links
        radioViewModel.getStationId();
        radioViewModel.getRemoteLinks();

        radioViewModel.getRemoteLinksLiveData().observe(this, resource -> {
            if (resource.status == Resource.Status.SUCCESS) {
                List<RadioStation> radioStations = RadioStationsHelper.createRadioStations(resource.data);
                radioListRecyclerViewAdapter.update(radioStations);
                updateMiniPlayer(getStationUsingSavedId());
            }
        });

        radioViewModel.getStationIdLivedata().observe(this, intResource -> {
            if (intResource.status == Resource.Status.SUCCESS) {
                lastStationId = intResource.data;
                radioListRecyclerViewAdapter.setSelectedStationId(lastStationId);
            }
        });

        radioViewModel.getSelectedStation().observe(this, radioStation -> {
            this.currentPlayingStation = radioStation;
            playerManager.setRadioStation(radioStation);
            radioListRecyclerViewAdapter.setSelectedStation(radioStation);

            if (lastStationId == radioStation.getId()) {
                playerManager.playOrStop();
            } else {
                playerManager.playFromMainActivity();
            }
            radioViewModel.saveStationId(radioStation.getId());

            hideKeyboard();
            updateMiniPlayer(radioStation);
        });
    }


    private void setupViewPagerAndTabs() {


        viewPagerAdapter = new ViewPagerAdapter(getSupportFragmentManager(), getLifecycle());
        viewPagerAdapter.addFragments();
        binding.viewPager.setAdapter(viewPagerAdapter);

        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("STATIONS"));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("LIVE"));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("DISCOVER"));

        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                tabPosition = tab.getPosition();
                binding.viewPager.setCurrentItem(tabPosition);
                viewPagerAdapter.notifyDataSetChanged();
                bottomSheetBehavior.setState(tabPosition == 0
                        ? BottomSheetBehavior.STATE_COLLAPSED
                        : BottomSheetBehavior.STATE_HIDDEN);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                binding.tabLayout.selectTab(binding.tabLayout.getTabAt(position));
            }
        });
    }


    private void setupSearchUI() {
        binding.ivSearch.setOnClickListener(view -> {
            if (searchVisible) {
                binding.etSearch.setVisibility(View.INVISIBLE);
                binding.ivClearSearch.setVisibility(View.INVISIBLE);
                searchVisible = false;
                hideKeyboard();
            } else {
                if (tabPosition != 0) {
                    binding.viewPager.setCurrentItem(0);
                }
                binding.etSearch.post(() -> {
                    binding.etSearch.requestFocus();
                    inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputMethodManager.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT);
                });

                binding.etSearch.setVisibility(View.VISIBLE);
                searchVisible = true;
                if (binding.etSearch.getText().length() > 0) {
                    binding.ivClearSearch.setVisibility(View.VISIBLE);
                }
            }
        });

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                radioListRecyclerViewAdapter.filter(editable.toString());
                binding.ivClearSearch.setVisibility(editable.length() > 0 ? View.VISIBLE : View.INVISIBLE);
            }
        });

        binding.ivClearSearch.setOnClickListener(v -> binding.etSearch.setText(""));
    }

    private void setupBroadcastReceiver() {
        IntentFilter eventFilter = new IntentFilter();
        eventFilter.addAction(StreamService.ACTION_EVENT_CHANGE);
        eventFilter.addAction(StreamService.ACTION_UPDATE_UI);

        EventReceiver eventReceiver = new EventReceiver();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(eventReceiver, eventFilter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(eventReceiver, eventFilter);
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        CacheUtil.clearAppCache(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        radioViewModel.setCurrentPage(binding.viewPager.getCurrentItem());
        radioViewModel.removeStreamLinkListener();
    }

    @Override
    protected void onResume() {
        super.onResume();

        Integer currentPage = radioViewModel.getCurrentPage().getValue();
        binding.viewPager.setCurrentItem(currentPage, false);
        binding.tabLayout.selectTab(binding.tabLayout.getTabAt(currentPage));


        if (searchVisible && binding.etSearch.getText().length() > 0) {
            binding.ivClearSearch.setVisibility(View.VISIBLE);
        } else {
            binding.ivClearSearch.setVisibility(View.INVISIBLE);
        }

        radioViewModel.getRemoteLinks();
        radioViewModel.getStationId();
    }


    //Broadcast Receiver
    public class EventReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String state = intent.getStringExtra(StreamService.EXTRA_STATE);
            radioListRecyclerViewAdapter.setState(state);
            radioListRecyclerViewAdapter.updateStation(currentPlayingStation);

            if (state.equals(StreamService.StreamStates.BUFFERING) || state.equals(StreamService.StreamStates.PREPARING)) {
                binding.miniPlayer.ivPlayMiniPlayerLayout.setVisibility(View.INVISIBLE);
                binding.miniPlayer.loadingAnimationMiniPlayerLayout.setVisibility(View.VISIBLE);
            } else if (state.equals(StreamService.StreamStates.PLAYING)) {
                binding.miniPlayer.ivPlayMiniPlayerLayout.setImageResource(R.drawable.pauseicon);
                binding.miniPlayer.ivPlayMiniPlayerLayout.setVisibility(View.VISIBLE);
                binding.miniPlayer.loadingAnimationMiniPlayerLayout.setVisibility(View.INVISIBLE);
            } else {
                binding.miniPlayer.ivPlayMiniPlayerLayout.setVisibility(View.VISIBLE);
                binding.miniPlayer.ivPlayMiniPlayerLayout.setImageResource(R.drawable.playicon);
                binding.miniPlayer.loadingAnimationMiniPlayerLayout.setVisibility(View.INVISIBLE);
            }
            //update mini player
            binding.miniPlayer.tvStatusMiniPlayerLayout.setText(state);
        }
    }

    void updateMiniPlayer(RadioStation radioStation) {
        binding.miniPlayer.ivLogoMiniPlayerLayout.setImageResource(radioStation.getSmallLogo());
        binding.miniPlayer.tvStationNameMiniPlayerLayout.setText(radioStation.getStationName());
    }

    void miniPlayerPlayPause() {
        radioViewModel.setSelectedStation(getStationUsingSavedId());
    }

    RadioStation getStationUsingSavedId() {
        RadioStation radioStation = new RadioStation(0, "", "", "", "", lastStationId);
        int position = radioListRecyclerViewAdapter.getPosOfStation(lastStationId);

        if (!(radioListRecyclerViewAdapter.stationListIsEmpty() || position == RecyclerView.NO_POSITION)) {
            radioStation = radioListRecyclerViewAdapter.getStationAtPosition(position);
        }
        return radioStation;
    }

    void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            String[] permissions = {android.Manifest.permission.POST_NOTIFICATIONS};
            ActivityCompat.requestPermissions(this, permissions, 0);
        }
    }


    public void sendFirebaseAnalytics(String stationName) {
        String event = stationName.toLowerCase().replace(" ", "");
        Bundle bundle = new Bundle();
        bundle.putString("station_name", stationName);
        firebaseAnalytics.logEvent(event, bundle);
    }

    public void hideKeyboard() {
        inputMethodManager.hideSoftInputFromWindow(binding.etSearch.getWindowToken(), 0);
        binding.etSearch.setVisibility(View.INVISIBLE);
        binding.ivClearSearch.setVisibility(View.INVISIBLE);
        searchVisible = false;
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.action_sort:

                if (tabPosition != 0) {
                    binding.viewPager.setCurrentItem(0);
                }
                SortDialog sortDialog = new SortDialog();
                sortDialog.show(getSupportFragmentManager(), "dialogueFragment");
                return true;
            case R.id.action_info:
                startActivity(new Intent(this, AboutFragment.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public RadioListRecyclerViewAdapter getAdapter() {
        return radioListRecyclerViewAdapter;
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }

}