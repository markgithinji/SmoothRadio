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
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.smoothradio.radio.core.ui.RadioViewModel;
import com.smoothradio.radio.core.ui.adapter.ViewPagerAdapter;
import com.smoothradio.radio.core.util.CacheUtil;
import com.smoothradio.radio.core.util.ConsentHelper;
import com.smoothradio.radio.core.util.PlayerManager;
import com.smoothradio.radio.databinding.ActivityMainBinding;
import com.smoothradio.radio.feature.about.ui.AboutFragment;
import com.smoothradio.radio.feature.radio_list.ui.adapter.RadioListRecyclerViewAdapter;
import com.smoothradio.radio.core.model.RadioStation;
import com.smoothradio.radio.service.StreamService;
import com.smoothradio.radio.feature.radio_list.util.SortDialog;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    // View Binding
    private ActivityMainBinding binding;

    // ViewModel
    private RadioViewModel radioViewModel;

    // Adapters
    private ViewPagerAdapter viewPagerAdapter;
    private RadioListRecyclerViewAdapter radioListRecyclerViewAdapter;

    // Services & Managers
    private PlayerManager playerManager;
    private InputMethodManager inputMethodManager;
    private FirebaseAnalytics firebaseAnalytics;

    // State
    private int tabPosition = 0;
    private int lastStationId = 0;
    private boolean isSearchVisible = false;

    // Components
    public BottomSheetBehavior<View> bottomSheetBehavior;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SplashScreen.installSplashScreen(this);// for displaying splash screen

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initializeComponents();
        setupObservers();
        setupUI();
        setupBroadcastReceiver();
        requestPermissions();
    }

    private void initializeComponents() {
        radioViewModel = new ViewModelProvider(this).get(RadioViewModel.class);
        radioListRecyclerViewAdapter = new RadioListRecyclerViewAdapter(new ArrayList<>()
                ,new RadioStationActionHandler(radioViewModel));
        playerManager = new PlayerManager(this);
        inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);//for Hiding KeyBoard
        firebaseAnalytics = FirebaseAnalytics.getInstance(this);
        bottomSheetBehavior = BottomSheetBehavior.from(binding.miniPlayer.bottomSheetLayout);

        new ConsentHelper(this).showConsentForm();
    }
    private void setupUI() {
        setSupportActionBar(binding.toolbar);
        setupViewPagerAndTabs();
        setupSearchUI();
        binding.miniPlayer.ivPlayMiniPlayerLayout.setOnClickListener(v -> miniPlayerPlayPause());
    }

    private void setupObservers() {

        radioViewModel.getPlayingStation().observe(this, station -> {
            if (station != null) {
                Log.d("MainActivity", "setupObservers: playing station found: " + station.getStationName());
                lastStationId = station.getId();
                updateMiniPlayer(station);
            } else {
                Log.d("MainActivity", "setupObservers: no playing station in Room, loading from saved ID");
                RadioStation savedStation = getStationUsingSavedId();
                updateMiniPlayer(savedStation);
            }
        });

        radioViewModel.getSelectedStation().observe(this, radioStation -> {
            hideKeyboard();
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
            if (isSearchVisible) {
                binding.etSearch.setVisibility(View.INVISIBLE);
                binding.ivClearSearch.setVisibility(View.INVISIBLE);
                isSearchVisible = false;
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
                isSearchVisible = true;
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

        EventReceiver eventReceiver = new EventReceiver();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(eventReceiver, eventFilter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(eventReceiver, eventFilter);
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            String[] permissions = {android.Manifest.permission.POST_NOTIFICATIONS};
            ActivityCompat.requestPermissions(this, permissions, 0);
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


        if (isSearchVisible && binding.etSearch.getText().length() > 0) {
            binding.ivClearSearch.setVisibility(View.VISIBLE);
        } else {
            binding.ivClearSearch.setVisibility(View.INVISIBLE);
        }

        radioViewModel.getRemoteLinks();
    }

    private void hideKeyboard() {
        inputMethodManager.hideSoftInputFromWindow(binding.etSearch.getWindowToken(), 0);
        binding.etSearch.setVisibility(View.INVISIBLE);
        binding.ivClearSearch.setVisibility(View.INVISIBLE);
        isSearchVisible = false;
    }

    private void updateMiniPlayer(RadioStation radioStation) {
        binding.miniPlayer.ivLogoMiniPlayerLayout.setImageResource(radioStation.getLogoResource());
        binding.miniPlayer.tvStationNameMiniPlayerLayout.setText(radioStation.getStationName());
    }

    private void miniPlayerPlayPause() {
        radioViewModel.setSelectedStation(getStationUsingSavedId());
    }

    public RadioStation getStationUsingSavedId() {
        RadioStation radioStation = new RadioStation(lastStationId,0, "", "", "", "", false,false);
        int position = radioListRecyclerViewAdapter.getPositionOfStation(lastStationId);

        if (!(radioListRecyclerViewAdapter.listIsEmpty() || position == RecyclerView.NO_POSITION)) {
            radioStation = radioListRecyclerViewAdapter.getStationAtPosition(position);
        }
        return radioStation;
    }
    public void sendFirebaseAnalytics(String stationName) {
        String event = stationName.toLowerCase().replace(" ", "");
        Bundle bundle = new Bundle();
        bundle.putString("station_name", stationName);
        firebaseAnalytics.logEvent(event, bundle);
    }
    public RadioListRecyclerViewAdapter getAdapter() {
        return radioListRecyclerViewAdapter;
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }


    private class EventReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String state = intent.getStringExtra(StreamService.EXTRA_STATE);

            if (state.equals(StreamService.StreamStates.BUFFERING) || state.equals(StreamService.StreamStates.PREPARING)) {
                showBufferingState();
            } else if (state.equals(StreamService.StreamStates.PLAYING)) {
                showPlayingState();
            } else {
                showStoppedState();
            }
            //update mini player
            binding.miniPlayer.tvStatusMiniPlayerLayout.setText(state);
        }
        private void showBufferingState() {
            binding.miniPlayer.ivPlayMiniPlayerLayout.setVisibility(View.INVISIBLE);
            binding.miniPlayer.loadingAnimationMiniPlayerLayout.setVisibility(View.VISIBLE);
        }

        private void showPlayingState() {
            binding.miniPlayer.ivPlayMiniPlayerLayout.setImageResource(R.drawable.pauseicon);
            binding.miniPlayer.ivPlayMiniPlayerLayout.setVisibility(View.VISIBLE);
            binding.miniPlayer.loadingAnimationMiniPlayerLayout.setVisibility(View.INVISIBLE);
        }

        private void showStoppedState() {
            binding.miniPlayer.ivPlayMiniPlayerLayout.setVisibility(View.VISIBLE);
            binding.miniPlayer.ivPlayMiniPlayerLayout.setImageResource(R.drawable.playicon);
            binding.miniPlayer.loadingAnimationMiniPlayerLayout.setVisibility(View.INVISIBLE);
        }
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
    public class RadioStationActionHandler implements RadioListRecyclerViewAdapter.RadioStationActionListener {
        private final RadioViewModel radioViewModel;

        public RadioStationActionHandler(RadioViewModel radioViewModel) {
            this.radioViewModel = radioViewModel;
        }

        @Override
        public void onStationSelected(RadioStation station) {
            radioViewModel.setSelectedStation(station);
        }

        @Override
        public void onToggleFavorite(RadioStation station, boolean isFavorite) {
            radioViewModel.updateFavoriteStatus(station.getId(), isFavorite);
        }

        @Override
        public void onRequestHideKeyboard() {
            hideKeyboard();
        }

        @Override
        public void onRequestshowToast(String message) {
            showToast(message);
        }

        private void showToast(String message) {
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
        }

    }

}