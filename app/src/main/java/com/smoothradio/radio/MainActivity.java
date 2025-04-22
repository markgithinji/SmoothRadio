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
import android.view.MenuInflater;
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
import com.google.firebase.firestore.FirebaseFirestore;
import com.smoothradio.radio.core.CacheUtils;
import com.smoothradio.radio.core.ConsentHelper;
import com.smoothradio.radio.core.RadioStationsHelper;
import com.smoothradio.radio.core.RadioViewModel;
import com.smoothradio.radio.core.Resource;
import com.smoothradio.radio.core.ViewPagerAdapter;
import com.smoothradio.radio.databinding.ActivityMainBinding;
import com.smoothradio.radio.feature.about.AboutFragment;
import com.smoothradio.radio.feature.radio_list.RadioListRecyclerViewAdapter;
import com.smoothradio.radio.feature.player.PlayerFragment;
import com.smoothradio.radio.model.RadioStation;
import com.smoothradio.radio.service.StreamService;
import com.smoothradio.radio.util.SortDialog;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // View binding
    public ActivityMainBinding binding;

    // ViewPager
    private ViewPagerAdapter viewPagerAdapter;
    private static int currentPage;
    private static int tabPosition;

    // Fragment
    public PlayerFragment playerFragment;

    // Adapter
    public RadioListRecyclerViewAdapter radioListRecyclerViewAdapter;

    // ViewModel
    public RadioViewModel radioViewModel;

    // Bottom sheet
    public BottomSheetBehavior bottomSheetBehavior;

    // UI state
    private boolean searchVisible = false;
    private static String lifecycleStage = "";

    // Input & keyboard
    private InputMethodManager inputMethodManager;

    // Firebase
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private FirebaseAnalytics firebaseAnalytics;

    // Receiver
    private EventReceiver eventReceiver;

    // Radio stations (local data)
    public final List<RadioStation> radioStationsList = new ArrayList<>();

    // Online database (static lists shared across app)
    public static ArrayList<String> linksFromTxt = new ArrayList<>();
    public static ArrayList<String> linksAfterUpdate = new ArrayList<>();



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);// for displaying splash screen

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        new ConsentHelper(this).showConsentForm();

        radioViewModel = new ViewModelProvider(this).get(RadioViewModel.class);

        radioListRecyclerViewAdapter = new RadioListRecyclerViewAdapter(radioStationsList);

        setupObservers();

        bottomSheetBehavior = BottomSheetBehavior.from(binding.miniPlayer.bottomSheetLayout);

        //Add fragment to get it later. Cannot be added after ViewPager creates fragment
        playerFragment = new PlayerFragment();
        getSupportFragmentManager().beginTransaction().add(playerFragment, "PlayerFragment").commit();

        setupViewPagerAndTabs();

        setupSearchUI();

        binding.miniPlayer.ivPlayMiniPlayerLayout.setOnClickListener(v -> miniPlayerPlayPause());

        setupBroadcastReceiver();

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions();
        }

        inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);//for Hiding KeyBoard

        firebaseAnalytics = FirebaseAnalytics.getInstance(this);

    }

    private void setupObservers() {
        // Init radio links
        radioViewModel.isFirstTime();
        radioViewModel.getStationId();
        radioViewModel.getLocalLinks();
        radioViewModel.getRemoteStreamLinks();

        radioViewModel.getIsFirstTimeLiveData().observe(this, resource -> {
            if (resource.status == Resource.Status.SUCCESS) {
                Boolean isFirstTime = resource.data;
                if (Boolean.TRUE.equals(isFirstTime)) {
                    radioViewModel.createInitialLinks();
                    radioViewModel.saveIsFirstTime(false);
                }
            }
        });
        radioViewModel.getLocalLinksLiveData().observe(this, resource -> {
            if (resource.status == Resource.Status.SUCCESS) {
                linksFromTxt.clear();
                linksFromTxt.addAll(resource.data);

                radioListRecyclerViewAdapter.update( RadioStationsHelper.createRadioStations(linksFromTxt));
                radioViewModel.getStationId();
            }
        });
        //update mini player
        radioViewModel.getStationIdLivedata().observe(this, intResource -> {
            if (intResource.status == Resource.Status.SUCCESS) {
                int stationId = intResource.data;
                radioListRecyclerViewAdapter.setSelectedStationId(stationId);
                int position = radioListRecyclerViewAdapter.getPosOfStation(stationId);
                if (position != -1) { // if list returns -1, it doesn't contain the station
                    updateMiniPlayer(radioListRecyclerViewAdapter.radioStationItems.get(position));
                }
            }
        });
        radioViewModel.getRemoteinksLiveData().observe(this, resource -> {
            if (resource.status == Resource.Status.SUCCESS) {
                List<String> links = resource.data;
                linksAfterUpdate = new ArrayList<>(links);

                if (!linksFromTxt.equals(linksAfterUpdate)) {
                    linksFromTxt.clear();
                    linksFromTxt.addAll(linksAfterUpdate);

                    radioListRecyclerViewAdapter.update(RadioStationsHelper.createRadioStations(linksFromTxt));

                    Toast.makeText(MainActivity.this, "Stations updated", Toast.LENGTH_SHORT).show();
                }
            }
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
            @Override public void onTabSelected(TabLayout.Tab tab) {
                tabPosition = tab.getPosition();
                binding.viewPager.setCurrentItem(tabPosition);
                viewPagerAdapter.notifyItemChanged(tabPosition);
                bottomSheetBehavior.setState(tabPosition == 0
                        ? BottomSheetBehavior.STATE_COLLAPSED
                        : BottomSheetBehavior.STATE_HIDDEN);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                binding.tabLayout.selectTab(binding.tabLayout.getTabAt(position));
            }
        });

        // Force fragment creation
        binding.viewPager.setCurrentItem(1);
        binding.viewPager.setCurrentItem(0);

        //ViewPager Swipe Sensitivity
        adjustViewPagerSwipeSensitivity();
    }

    private void adjustViewPagerSwipeSensitivity() {
        new Thread(() -> {
            try {
                Field recyclerViewField = ViewPager2.class.getDeclaredField("mRecyclerView");
                recyclerViewField.setAccessible(true);
                RecyclerView recyclerView = (RecyclerView) recyclerViewField.get(binding.viewPager);

                Field touchSlopField = RecyclerView.class.getDeclaredField("mTouchSlop");
                touchSlopField.setAccessible(true);
                int touchSlop = (int) touchSlopField.get(recyclerView);
                touchSlopField.set(recyclerView, touchSlop * 3);
            } catch (Exception ignored) {}
        }).start();
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
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable editable) {
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
        eventReceiver = new EventReceiver();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(eventReceiver, eventFilter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(eventReceiver, eventFilter);
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        lifecycleStage = "onDestroy";
        CacheUtils.clearAppCache(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        currentPage =  binding.viewPager.getCurrentItem();
        radioViewModel.removeStreamLinkListener();
    }

    @Override
    protected void onResume() {
        super.onResume();
        lifecycleStage = "onResume";

        binding.tabLayout.selectTab( binding.tabLayout.getTabAt(currentPage));
        binding.viewPager.setCurrentItem(currentPage);

        if (searchVisible &&  binding.etSearch.getText().length() > 0) {
            binding.ivClearSearch.setVisibility(View.VISIBLE);
        } else {
            binding.ivClearSearch.setVisibility(View.INVISIBLE);
        }

        radioViewModel.getRemoteStreamLinks();
    }

    public void play(RadioStation radioStation) {
        playerFragment = (PlayerFragment) getSupportFragmentManager().findFragmentByTag("PlayerFragment");

        if (playerFragment != null) {
            Log.d("Mainactivity", "playFromMainActivity: " + radioStation.getId());
            playerFragment.playFromMainActivity(radioStation);
        }
        radioViewModel.saveStationId(radioStation.getId());

        hideKeyboard();

        //update mini player
        updateMiniPlayer(radioStation);
    }


    //Broadcast Receiver
    public class EventReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String state = intent.getStringExtra(StreamService.EXTRA_STATE);

            if (!lifecycleStage.equals("onDestroy")) {
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
        playerFragment.playOrStop();
        if (playerFragment.getIsPlaying())//if is playing
        {
            binding.miniPlayer.ivPlayMiniPlayerLayout.setImageResource(R.drawable.playicon);
        } else {
            binding.miniPlayer.ivPlayMiniPlayerLayout.setImageResource(R.drawable.pauseicon);
        }
    }

    void requestPermissions() {
        String[] permissions = {android.Manifest.permission.POST_NOTIFICATIONS};
        ActivityCompat.requestPermissions(this, permissions, 0);
    }

    public void sendFirebaseAnalytics(String stationName) {
        String event = stationName.toLowerCase().replace(" ", "");
        Bundle bundle = new Bundle();
        bundle.putString("station_name", stationName);
        firebaseAnalytics.logEvent(event, bundle);
    }

    public void hideKeyboard() {
        inputMethodManager.hideSoftInputFromWindow( binding.etSearch.getWindowToken(), 0);
        binding.etSearch.setVisibility(View.INVISIBLE);
        binding.ivClearSearch.setVisibility(View.INVISIBLE);
        searchVisible = false;
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
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

                Intent intent = new Intent(this, AboutFragment.class);
                startActivity(intent);

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}