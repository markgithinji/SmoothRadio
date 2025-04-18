package com.smoothradio.radio;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.tabs.TabLayout;
import com.google.android.ump.ConsentForm;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.smoothradio.radio.core.ViewPagerAdapter;
import com.smoothradio.radio.feature.about.AboutFragment;
import com.smoothradio.radio.feature.radio_list.RadioListRecyclerViewAdapter;
import com.smoothradio.radio.feature.player.PlayerFragment;
import com.smoothradio.radio.model.RadioStation;
import com.smoothradio.radio.util.SortDialog;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {
    //ui
    public ViewPager2 viewPager;
    public RadioListRecyclerViewAdapter musicListRecyclerViewAdapter;
    ViewPagerAdapter viewPagerAdapter;
    TabLayout tabLayout;
    ImageView ivSearch;
    ImageView ivClearSearch;
    EditText etSearch;
    AdView adView;

    //Consent form
    private ConsentInformation consentInformation;
    // Use an atomic boolean to initialize the Google Mobile Ads SDK and load ads once.
    private final AtomicBoolean isMobileAdsInitializeCalled = new AtomicBoolean(false);

    boolean searchVisible = false;
    static int currentPage;
    static int tabPosition;
    ImageView ivMiniPlayerLogo;
    LottieAnimationView loadingAnimationMiniPlayerLayout;
    TextView tvMiniPlayerStationName;
    TextView tvStatusMiniPlayer;
    ImageView ivPlayMiniPlayer;

    androidx.appcompat.widget.Toolbar toolbar;
    static String lifecycleStage = "";
    public static final String Action_Change_Listener_Name = "SmoothEventChangeListener";
    public static final String Action_Update_UI = "SmoothstateFromService";
    EventReceiver eventReceiver;
    InputMethodManager inputMethodManager;
    public ConstraintLayout bottomSheetLayout;
    FirebaseFirestore db = FirebaseFirestore.getInstance();
    public BottomSheetBehavior bottomSheetBehavior;
    //TimerDialog
    CoordinatorLayout coordinatorLayout;
    //container
    public List<RadioStation> radioStationsList = new ArrayList<>();
    //Instances
    public PlayerFragment playerFragment;
    public RadioListRecyclerViewAdapter radioListRecyclerViewAdapter;
    //OnlineDatabase
    public static ArrayList<String> linksFromTxt = new ArrayList<>();
    public static ArrayList<String> linksAfterUpdate = new ArrayList<>();
    ListenerRegistration listenerRegistration;
    SharedPreferences sharedPreferences;
    SharedPreferences.Editor prefsEditor;
    //Analytics
    FirebaseAnalytics firebaseAnalytics;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);// for displaying splash screen

        setContentView(R.layout.activity_main);

        showConsentForm();

        //Initial Check
        sharedPreferences = getSharedPreferences("PlayerFragmentSharedPref", Context.MODE_PRIVATE);
        prefsEditor = sharedPreferences.edit();
        boolean firstTime = sharedPreferences.getBoolean("isFirstTime", true);
        if (firstTime) {
            createInitialTxt();
            prefsEditor.putBoolean("isFirstTime", false);
            prefsEditor.apply();
        }


        txtToArrayList();
        createRadioStations();

        tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.vp);
        ivSearch = findViewById(R.id.ivSearch);
        etSearch = findViewById(R.id.etSearch);
        ivClearSearch = findViewById(R.id.ivClearSearch);
        adView = findViewById(R.id.adView);
        coordinatorLayout = findViewById(R.id.coordinatorLayout);
        ivMiniPlayerLogo = findViewById(R.id.ivLogoMiniPlayerLayout);
        tvMiniPlayerStationName = findViewById(R.id.tvStationNameMiniPlayerLayout);
        tvStatusMiniPlayer = findViewById(R.id.tvStatusMiniPlayerLayout);
        ivPlayMiniPlayer = findViewById(R.id.ivPlayMiniPlayerLayout);
        loadingAnimationMiniPlayerLayout = findViewById(R.id.loadingAnimationMiniPlayerLayout);
        radioListRecyclerViewAdapter = new RadioListRecyclerViewAdapter(radioStationsList);
        bottomSheetLayout = findViewById(R.id.bottomSheetLayout);
        toolbar = findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);

        //loading animation
        loadingAnimationMiniPlayerLayout.playAnimation();
        loadingAnimationMiniPlayerLayout.setVisibility(View.INVISIBLE);

        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetLayout);

        //Add fragment to get it later. Cannot be added after ViewPager creates fragment
        playerFragment = new PlayerFragment();
        getSupportFragmentManager().beginTransaction().add(playerFragment, "PlayerFragment").commit();


        viewPagerAdapter = new ViewPagerAdapter(getSupportFragmentManager(), getLifecycle());
        viewPagerAdapter.addFragments();
        viewPager.setAdapter(viewPagerAdapter);

        tabLayout.addTab(tabLayout.newTab().setText("STATIONS"));
        tabLayout.addTab(tabLayout.newTab().setText("LIVE"));
        tabLayout.addTab(tabLayout.newTab().setText("DISCOVER"));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                tabPosition = tab.getPosition();
                viewPager.setCurrentItem(tabPosition);
                viewPagerAdapter.notifyDataSetChanged();
                if (tabPosition != 0) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                } else {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                tabLayout.selectTab(tabLayout.getTabAt(position));
            }
        });

        //forced oncreate due to all fragments not created at the same time
        viewPager.setCurrentItem(1);
        viewPager.setCurrentItem(0);

        //ViewPager Swipe Sensitivity
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final Field recyclerViewField = ViewPager2.class.getDeclaredField("mRecyclerView");
                    recyclerViewField.setAccessible(true);

                    final RecyclerView recyclerView = (RecyclerView) recyclerViewField.get(viewPager);

                    final Field touchSlopField = RecyclerView.class.getDeclaredField("mTouchSlop");
                    touchSlopField.setAccessible(true);

                    final int touchSlop = (int) touchSlopField.get(recyclerView);
                    touchSlopField.set(recyclerView, touchSlop * 3);//6 is empirical value
                } catch (Exception ignore) {
                }
            }
        }).start();

        ivSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (searchVisible) {
                    etSearch.setVisibility(View.INVISIBLE);
                    ivClearSearch.setVisibility(View.INVISIBLE);
                    searchVisible = false;
                    hideKeyboard();
                } else {
                    if (tabPosition != 0) {
                        viewPager.setCurrentItem(0);
                    }
                    etSearch.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            etSearch.requestFocus();
                            inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                            inputMethodManager.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT);
                        }
                    }, 0);

                    etSearch.setVisibility(View.VISIBLE);
                    searchVisible = true;
                    if (etSearch.getText().length() > 0) {
                        ivClearSearch.setVisibility(View.VISIBLE);
                    }
                }
            }
        });
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                radioListRecyclerViewAdapter.filter(editable.toString());
                if (editable.length() == 0) {
                    ivClearSearch.setVisibility(View.INVISIBLE);
                } else {
                    ivClearSearch.setVisibility(View.VISIBLE);
                }
            }
        });

        ivClearSearch.setOnClickListener(v -> etSearch.setText(""));

        ivPlayMiniPlayer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                miniPlayerPlayPause();
            }
        });

        //BroadcastListener
        IntentFilter eventFilter = new IntentFilter();
        eventFilter.addAction(Action_Change_Listener_Name);//Player States
        eventFilter.addAction(Action_Update_UI);//update mini player on resume
        eventReceiver = new EventReceiver();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(eventReceiver, eventFilter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(eventReceiver, eventFilter);
        }

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions();
        }

        inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);//for Hide KeyBoard
        firebaseAnalytics = FirebaseAnalytics.getInstance(this);///////////////////////////////////////////////////////////////////////////////////////////////////

    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        lifecycleStage = "onDestroy";
        if (listenerRegistration != null) {
            listenerRegistration.remove();
        }
        deleteCache(MainActivity.this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        currentPage = viewPager.getCurrentItem();
        if (listenerRegistration != null) {
            listenerRegistration.remove();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        lifecycleStage = "onResume";

        tabLayout.selectTab(tabLayout.getTabAt(currentPage));
        viewPager.setCurrentItem(currentPage);

        if (searchVisible && etSearch.getText().length() > 0) {
            ivClearSearch.setVisibility(View.VISIBLE);
        } else {
            ivClearSearch.setVisibility(View.INVISIBLE);
        }

        //update mini player
        int stationId = sharedPreferences.getInt("stationId", 0);
        int position = radioListRecyclerViewAdapter.getPosOfStation(stationId);

        RadioStation lastRadioStation = new RadioStation(0, "", "", "", "", stationId);
        if (position != -1)// if list returns -1, it doesn't contain the station
        {
            updateMiniPlayer(radioListRecyclerViewAdapter.radioStationItems.get(position));
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                listenerRegistration = db.collection("links").orderBy("index").addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(QuerySnapshot value, FirebaseFirestoreException error) {
                        if (error != null) {
                            //Toast.makeText(MainActivity.this, "Error updating from server", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if (value != null && !value.isEmpty()) {
                            File file = new File(getFilesDir(), "file.txt");
                            BufferedWriter writer = null;
                            try {
                                writer = new BufferedWriter(new FileWriter(file));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            for (DocumentSnapshot document : value.getDocuments()) {
                                try {
                                    String data = document.getString("link");
                                    if (data == null) {
                                        writer.write("");
                                        writer.newLine();
                                    } else {
                                        writer.write(data);
                                        writer.newLine();
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    //Toast.makeText(getApplicationContext(), "no write", Toast.LENGTH_LONG).show();
                                }
                            }
                            try {
                                writer.flush();
                                writer.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            //txtToArrayList();
                            txtToArrayListAfterUpdate();
                            if (!linksFromTxt.equals(linksAfterUpdate)) {
                                linksFromTxt.clear();
                                linksFromTxt.addAll(linksAfterUpdate);
                                createRadioStations();
                                radioListRecyclerViewAdapter.stationListCopy.clear();
                                radioListRecyclerViewAdapter.stationListCopyCopy.clear();
                                radioListRecyclerViewAdapter.radioStationItems.clear();
                                radioListRecyclerViewAdapter.stationListCopy.addAll(radioStationsList);
                                radioListRecyclerViewAdapter.stationListCopyCopy.addAll(radioStationsList);
                                radioListRecyclerViewAdapter.radioStationItems.addAll(radioStationsList);
                                radioListRecyclerViewAdapter.sortPopular();

                                Toast.makeText(MainActivity.this, "stations updated", Toast.LENGTH_SHORT).show();
                            }

                        } else {
                            //Toast.makeText(MainActivity.this, "Error updating from server", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();

    }

    public void play(RadioStation radioStation) {
        playerFragment = (PlayerFragment) getSupportFragmentManager().findFragmentByTag("PlayerFragment");
        assert playerFragment != null;
        playerFragment.playFromMainActivity(radioStation);

        prefsEditor.putInt("stationId", radioStation.getId());
        prefsEditor.commit();

        hideKeyboard();

        //update mini player
        updateMiniPlayer(radioStation);
    }


    //Broadcast Receiver
    public class EventReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String state = intent.getStringExtra("state");
            if (state == null) {
                state = intent.getStringExtra("stateUI");//for restoring ui onResume
            }
            if (!lifecycleStage.equals("onDestroy")) {
                if (state.equals("Buffering") || state.equals("Preparing Audio")) {
                    ivPlayMiniPlayer.setVisibility(View.INVISIBLE);
                    loadingAnimationMiniPlayerLayout.setVisibility(View.VISIBLE);
                } else if (state.equals("Playing")) {
                    ivPlayMiniPlayer.setImageResource(R.drawable.pauseicon);
                    ivPlayMiniPlayer.setVisibility(View.VISIBLE);
                    loadingAnimationMiniPlayerLayout.setVisibility(View.INVISIBLE);
                } else {
                    ivPlayMiniPlayer.setVisibility(View.VISIBLE);
                    ivPlayMiniPlayer.setImageResource(R.drawable.playicon);
                    loadingAnimationMiniPlayerLayout.setVisibility(View.INVISIBLE);
                }
            }
            //update mini player
            tvStatusMiniPlayer.setText(state);
        }
    }

    void updateMiniPlayer(RadioStation radioStation) {
        ivMiniPlayerLogo.setImageResource(radioStation.getSmallLogo());
        tvMiniPlayerStationName.setText(radioStation.getStationName());
    }

    void miniPlayerPlayPause() {
        playerFragment.playOrStop();
        if (playerFragment.getIsPlaying())/////notplaying
        {
            ivPlayMiniPlayer.setImageResource(R.drawable.playicon);
        } else {
            ivPlayMiniPlayer.setImageResource(R.drawable.pauseicon);
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
        firebaseAnalytics.logEvent(event, bundle);//////////////////////////////////////////////////////////////////////////////////////////
    }

    public void hideKeyboard() {
        inputMethodManager.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
        etSearch.setVisibility(View.INVISIBLE);
        ivClearSearch.setVisibility(View.INVISIBLE);
        searchVisible = false;
    }

    void createRadioStations() {
        radioStationsList.clear();

        radioStationsList.add(new RadioStation(R.drawable.hopefm, "HOPE FM", "93.3", "NAIROBI", linksFromTxt.get(0), 0));

        radioStationsList.add(new RadioStation(R.drawable.soundcityradiologo, "SOUNDCITY RADIO", "88.5", "NAIROBI", linksFromTxt.get(1), 1));
        radioStationsList.add(new RadioStation(R.drawable.kissfm, "KISS FM", "100.3", "NAIROBI", linksFromTxt.get(2), 2));
        radioStationsList.add(new RadioStation(R.drawable.radio47logo, "RADIO 47", "103.0", "NAIROBI", linksFromTxt.get(228), 228));
        radioStationsList.add(new RadioStation(R.drawable.nrg0, "NRG RADIO", "97.1", "NAIROBI", linksFromTxt.get(3), 3));
        radioStationsList.add(new RadioStation(R.drawable.radiocitizenlogo, "RADIO CITIZEN", "106.7", "NAIROBI", linksFromTxt.get(203), 203));
        radioStationsList.add(new RadioStation(R.drawable.inooro, "INOORO FM", "98.9", "NAIROBI", linksFromTxt.get(4), 4));
        //radioStationsList.add(new RadioStation(R.drawable.njatafmlogo,"NJATA FM","97.2","NAIROBI",linksFromTxt.get(206)));
        radioStationsList.add(new RadioStation(R.drawable.nrgchoiceradiologo, "CHOICE RADIO", "87.7", "NAIROBI", linksFromTxt.get(5), 5));

        radioStationsList.add(new RadioStation(R.drawable.hot96logo, "HOT 96", "96.0", "NAIROBI", linksFromTxt.get(6), 6));
        radioStationsList.add(new RadioStation(R.drawable.homeboys, "HOMEBOYS RADIO", "103.5", "NAIROBI", linksFromTxt.get(7), 7));
        radioStationsList.add(new RadioStation(R.drawable.classic105_logo, "CLASSIC 105", "105.2", "NAIROBI", linksFromTxt.get(8), 8));
        radioStationsList.add(new RadioStation(R.drawable.radiojambologo, "RADIO JAMBO", "97.5", "NAIROBI", linksFromTxt.get(9), 9));
        radioStationsList.add(new RadioStation(R.drawable.rogueradiologo, "ROGUE RADIO", "--.--", "NAIROBI", linksFromTxt.get(26), 26));
        radioStationsList.add(new RadioStation(R.drawable.tracefmlogo, "TRACE FM", "95.3", "NAIROBI", linksFromTxt.get(30), 30));
        radioStationsList.add(new RadioStation(R.drawable.corofmlogo, "CORO FM", "99.5", "NAIROBI", linksFromTxt.get(10), 10));
        radioStationsList.add(new RadioStation(R.drawable.kameme, "KAMEME FM", "101.1", "NAIROBI", linksFromTxt.get(11), 11));
        radioStationsList.add(new RadioStation(R.drawable.ghettoradiologo, "GHETTO RADIO", "89.5", "NAIROBI", linksFromTxt.get(12), 12));
        radioStationsList.add(new RadioStation(R.drawable.radiomaishalogo, "RADIO MAISHA", "102.7", "NAIROBI", linksFromTxt.get(13), 13));
        radioStationsList.add(new RadioStation(R.drawable.easyfmlogo, "EASY FM", "96.3", "NAIROBI", linksFromTxt.get(14), 14));
        radioStationsList.add(new RadioStation(R.drawable.ramogilogo, "RAMOGI FM", "107.1", "NAIROBI", linksFromTxt.get(15), 15));
        radioStationsList.add(new RadioStation(R.drawable.egesalogo, "EGESA FM", "98.6", "NAIROBI", linksFromTxt.get(16), 16));
        radioStationsList.add(new RadioStation(R.drawable.mulembelogo, "MULEMBE FM", "97.9", "NAIROBI", linksFromTxt.get(17), 17));
        radioStationsList.add(new RadioStation(R.drawable.musyilogo, "MUSYI FM", "102.2", "NAIROBI", linksFromTxt.get(18), 18));
        radioStationsList.add(new RadioStation(R.drawable.chamgeilogo, "CHAMGEI FM", "95.0", "NAIROBI", linksFromTxt.get(19), 19));
        radioStationsList.add(new RadioStation(R.drawable.kubambaradiologo, "KUBAMBA RADIO", "--.--", "NAIROBI", linksFromTxt.get(25), 25));
        radioStationsList.add(new RadioStation(R.drawable.truthfmlogo, "TRUTH FM", "90.7", "NAIROBI", linksFromTxt.get(27), 27));
        radioStationsList.add(new RadioStation(R.drawable.familyradiologo, "FAMILY RADIO", "103.9", "NAIROBI", linksFromTxt.get(28), 28));
        radioStationsList.add(new RadioStation(R.drawable.jesusislordradiologo, "JESUSISLORD RADIO", "105.3", "NAKURU", linksFromTxt.get(29), 29));
        radioStationsList.add(new RadioStation(R.drawable.xfmlogo, "SMOOTH FM", "105.5", "NAIROBI", linksFromTxt.get(31), 31));
        radioStationsList.add(new RadioStation(R.drawable.kigoocofmlogo, "KIGOOCO FM", "98.6", "NAIROBI", linksFromTxt.get(32), 32));
        radioStationsList.add(new RadioStation(R.drawable.kikuyudiasporaradiologo, "KENYA DIASPORA RADIO", "--.--", "ALABAMA", linksFromTxt.get(38), 38));
        radioStationsList.add(new RadioStation(R.drawable.mbciradiologo, "MBCI RADIO", "89.5", "NAKURU", linksFromTxt.get(33), 33));
        radioStationsList.add(new RadioStation(R.drawable.gfnradiologo, "GFN FM", "90.1", "TURKANA", linksFromTxt.get(224), 224));
        radioStationsList.add(new RadioStation(R.drawable.nationfmlogo, "NATION FM", "96.3", "NAIROBI", linksFromTxt.get(44), 44));
        radioStationsList.add(new RadioStation(R.drawable.capitalfmlogo, "CAPITAL FM", "98.4", "NAIROBI", linksFromTxt.get(54), 54));

        radioStationsList.add(new RadioStation(R.drawable.gukenafmlogo, "GUKENA FM", "92.8", "NAIROBI", linksFromTxt.get(41), 41));
        radioStationsList.add(new RadioStation(R.drawable.muugalogo, "MUUGA FM", "94.2", "NAIROBI", linksFromTxt.get(20), 20));
        radioStationsList.add(new RadioStation(R.drawable.sulwelogo, "SULWE FM", "89.6", "NAIROBI", linksFromTxt.get(21), 21));
        radioStationsList.add(new RadioStation(R.drawable.wimwarologo, "WIMWARO FM", "93.0", "NAIROBI", linksFromTxt.get(22), 22));
        radioStationsList.add(new RadioStation(R.drawable.baharilogo, "BAHARI FM", "90.4", "MOMBASA", linksFromTxt.get(23), 23));
        radioStationsList.add(new RadioStation(R.drawable.vuukalogo, "VUUKA FM", "95.4", "NAIROBI", linksFromTxt.get(24), 24));
        radioStationsList.add(new RadioStation(R.drawable.lubaofmlogo, "LUBAO FM", "102.2", "KAKAMEGA", linksFromTxt.get(92), 92));
        radioStationsList.add(new RadioStation(R.drawable.iqrafmlogo, "IQRA FM", "95.0", "NAIROBI", linksFromTxt.get(34), 34));

        radioStationsList.add(new RadioStation(R.drawable.pwanifmlogo, "PWANI FM", "103.1", "MOMBASA", linksFromTxt.get(35), 35));
        radioStationsList.add(new RadioStation(R.drawable.sautiyapwanifmlogo, "SAUTIYAPWANI FM", "94.2", "MOMBASA", linksFromTxt.get(36), 36));
        radioStationsList.add(new RadioStation(R.drawable.kbcradiotaifalogo, "KBC RADIO TAIFA", "92.9", "NAIROBI", linksFromTxt.get(37), 37));
        radioStationsList.add(new RadioStation(R.drawable.taachfmlogo, "TAACH FM", "95.1", "ELDORET", linksFromTxt.get(39), 39));
        radioStationsList.add(new RadioStation(R.drawable.eastfmlogo, "EAST FM", "106.3", "NAIROBI", linksFromTxt.get(40), 40));
        radioStationsList.add(new RadioStation(R.drawable.kassfmlogo, "KASS FM", "89.1", "NAIROBI", linksFromTxt.get(42), 42));
        radioStationsList.add(new RadioStation(R.drawable.soundasialogo, "SOUNDASIA RADIO", "88.0", "NAIROBI", linksFromTxt.get(43), 43));
        radioStationsList.add(new RadioStation(R.drawable.wauminilogo, "RADIO WAUMINI", "88.3", "NAIROBI", linksFromTxt.get(45), 45));

        radioStationsList.add(new RadioStation(R.drawable.bibiliahusemalogo, "BIBILIA HUSEMA FM", "96.7", "NAIROBI", linksFromTxt.get(46), 46));
        radioStationsList.add(new RadioStation(R.drawable.heroradiologo, "HERO RADIO", "99.0", "NAKURU", linksFromTxt.get(47), 47));
        radioStationsList.add(new RadioStation(R.drawable.milelefmlogo, "MILELE FM", "104.8", "NAIROBI", linksFromTxt.get(48), 48));
        radioStationsList.add(new RadioStation(R.drawable.getembefmlogo, "GETEMBE FM", "102.7", "KISII", linksFromTxt.get(207), 207));
        radioStationsList.add(new RadioStation(R.drawable.inkafmlogo, "INKA FM", "93.7", "KISII", linksFromTxt.get(208), 208));
        radioStationsList.add(new RadioStation(R.drawable.radiokayalogo, "RADIO KAYA", "93.1", "NAIROBI", linksFromTxt.get(49), 49));
        radioStationsList.add(new RadioStation(R.drawable.spicefmlogo, "SPICE FM", "94.4", "NAIROBI", linksFromTxt.get(50), 50));
        radioStationsList.add(new RadioStation(R.drawable.barakafmlogo, "BARAKA FM", "95.5", "MOMBASA", linksFromTxt.get(51), 51));
        radioStationsList.add(new RadioStation(R.drawable.radioinjililogo, "RADIO INJILI", "103.7", "KERICHO", linksFromTxt.get(52), 52));
        radioStationsList.add(new RadioStation(R.drawable.athianilogo, "ATHIANI FM", "99.2", "NAIROBI", linksFromTxt.get(53), 53));
        radioStationsList.add(new RadioStation(R.drawable.beatlocklogo, "BEATLOCK RADIO", "--.--", "NAIROBI", linksFromTxt.get(55), 55));
        radioStationsList.add(new RadioStation(R.drawable.pearlxtralogo, "PEARL XTRA FM", "--.--", "NAIROBI", linksFromTxt.get(56), 56));
        radioStationsList.add(new RadioStation(R.drawable.theupperroomlogo, "THE UPPERROOM FM", "--.--", "NAIROBI", linksFromTxt.get(57), 57));
        radioStationsList.add(new RadioStation(R.drawable.lulufmlogo, "LULU FM", "91.0", "MOMBASA", linksFromTxt.get(58), 58));
        radioStationsList.add(new RadioStation(R.drawable.pearlradiologo, "PEARL RADIO", "96.9", "NAIROBI", linksFromTxt.get(59), 59));

        radioStationsList.add(new RadioStation(R.drawable.imaniradiologo, "IMANI RADIO", "88.8", "KITALE", linksFromTxt.get(60), 60));
        radioStationsList.add(new RadioStation(R.drawable.lionafriqradiologo, "LIONAFRIQ RADIO", "--.--", "NAIROBI", linksFromTxt.get(61), 61));
        radioStationsList.add(new RadioStation(R.drawable.nrg_hiphop_logo, "NRG HIPHOP", "--.--", "NAIROBI", linksFromTxt.get(211), 211));
        radioStationsList.add(new RadioStation(R.drawable.nrg_dancehall_logo, "NRG DANCEHALL", "--.--", "NAIROBI", linksFromTxt.get(212), 212));
        radioStationsList.add(new RadioStation(R.drawable.nrg_gospel_logo, "NRG GOSPEL", "--.--", "NAIROBI", linksFromTxt.get(213), 213));
        radioStationsList.add(new RadioStation(R.drawable.nrg_afrobeats_logo, "NRG AFROBEATS", "--.--", "NAIROBI", linksFromTxt.get(214), 214));
        radioStationsList.add(new RadioStation(R.drawable.nrg_mixology_logo, "NRG MIXOLOGY", "--.--", "NAIROBI", linksFromTxt.get(215), 215));
        radioStationsList.add(new RadioStation(R.drawable.nrg_rnb_logo, "NRG RNB", "--.--", "NAIROBI", linksFromTxt.get(216), 216));
        radioStationsList.add(new RadioStation(R.drawable.nrg_jazz_logo, "NRG JAZZ", "--.--", "NAIROBI", linksFromTxt.get(217), 217));
        radioStationsList.add(new RadioStation(R.drawable.longaradiologo, "LONGA RADIO", "--.--", "NAIROBI", linksFromTxt.get(204), 204));
        radioStationsList.add(new RadioStation(R.drawable.kayufmlogo, "KAYU FM", "91.0", "NAIROBI", linksFromTxt.get(226), 226));
        radioStationsList.add(new RadioStation(R.drawable.flamingoradiologo, "FLAMINGO FM", "93.7", "NAKURU", linksFromTxt.get(62), 62));
        radioStationsList.add(new RadioStation(R.drawable.pilipilifmlogo, "PILIPILI FM", "99.5", "NAIROBI", linksFromTxt.get(63), 63));
        radioStationsList.add(new RadioStation(R.drawable.kerioradiologo, "KERIO RADIO", "87.6", "BARINGO", linksFromTxt.get(209), 209));
        radioStationsList.add(new RadioStation(R.drawable.tausifmlogo, "TAUSI FM", "--.--", "NAIROBI", linksFromTxt.get(210), 210));
        radioStationsList.add(new RadioStation(R.drawable.hillsidefmlogo, "HILLSIDE FM", "--.--", "NAIROBI", linksFromTxt.get(229), 229));
        radioStationsList.add(new RadioStation(R.drawable.pefaradoilogo, "PEFA RADIO", "--.--", "NAIROBI", linksFromTxt.get(230), 230));
        radioStationsList.add(new RadioStation(R.drawable.mayianfmlogo, "MAYIAN FM", "100.7", "NAROK", linksFromTxt.get(64), 64));
        radioStationsList.add(new RadioStation(R.drawable.merufmlogo, "MERU FM", "100.3", "MERU", linksFromTxt.get(65), 65));
        radioStationsList.add(new RadioStation(R.drawable.emoofmlogo, "EMOO FM", "104.2", "NAIROBI", linksFromTxt.get(66), 66));
        radioStationsList.add(new RadioStation(R.drawable.mutongoifmlogo, "MUTONGOI FM", "103.3", "KITUI", linksFromTxt.get(223), 223));
        radioStationsList.add(new RadioStation(R.drawable.mwagofmlogo, "MWANGO FM", "97.5", "NAIROBI", linksFromTxt.get(218), 218));
        radioStationsList.add(new RadioStation(R.drawable.nosimfmlogo, "NOSIM FM", "90.5", "NAIROBI", linksFromTxt.get(219), 219));
        radioStationsList.add(new RadioStation(R.drawable.osiepefmlogo, "OSIEPE FM", "96.8", "SIAYA", linksFromTxt.get(220), 220));
        radioStationsList.add(new RadioStation(R.drawable.radiosalaamlogo, "RADIO SALAAM", "90.7", "MOMBASA", linksFromTxt.get(67), 67));
        radioStationsList.add(new RadioStation(R.drawable.radiorahmalogo, "RADIO RAHMA", "91.5", "MOMBASA", linksFromTxt.get(68), 68));
        radioStationsList.add(new RadioStation(R.drawable.asylumradiologo, "ASYLUM RADIO", "--.--", "NAIROBI", linksFromTxt.get(69), 69));
        radioStationsList.add(new RadioStation(R.drawable.kbcenglishservicelogo, "KBC ENGLISH SERVICE", "95.6", "NAIROBI", linksFromTxt.get(70), 70));
        radioStationsList.add(new RadioStation(R.drawable.mbaitufmlogo, "MBAITU FM", "92.5", "MACHAKOS", linksFromTxt.get(71), 71));
        radioStationsList.add(new RadioStation(R.drawable.radio_maria, "RADIO MARIA", "90.0", "NAIROBI", linksFromTxt.get(72), 72));
        radioStationsList.add(new RadioStation(R.drawable.upendoradiologo, "UPENDO RADIO", "89.4", "ELDORET", linksFromTxt.get(73), 73));
        radioStationsList.add(new RadioStation(R.drawable.hiphopginlogo, "HIPHOPGIN", "--.--", "NAIROBI", linksFromTxt.get(74), 74));
        radioStationsList.add(new RadioStation(R.drawable.hiphopdailylogo, "HIPHOP DAILY", "97.5", "NAIROBI", linksFromTxt.get(75), 75));
        radioStationsList.add(new RadioStation(R.drawable.rfmlogo, "R FM", "99.9", "LIMURU", linksFromTxt.get(79), 79));
        radioStationsList.add(new RadioStation(R.drawable.tulizafmlogo, "TULIZA FM", "94.2", "MERU", linksFromTxt.get(76), 76));
        radioStationsList.add(new RadioStation(R.drawable.kihootofmlogo, "KIHOOTO FM", "91.2", "NAIROBI", linksFromTxt.get(77), 77));
        radioStationsList.add(new RadioStation(R.drawable.kisiifmlogo, "KISII FM", "94.1", "NAIROBI", linksFromTxt.get(78), 78));
        radioStationsList.add(new RadioStation(R.drawable.seitofmlogo, "SEITO FM", "100.3", "NAIROBI", linksFromTxt.get(80), 80));
        radioStationsList.add(new RadioStation(R.drawable.sifafmlogo, "SIFA FM", "101.2", "MARSABIT", linksFromTxt.get(81), 81));
        radioStationsList.add(new RadioStation(R.drawable.countyfmlogo, "COUNTY FM", "90.3", "KITUI", linksFromTxt.get(82), 82));
        radioStationsList.add(new RadioStation(R.drawable.sidaifmlogo, "SIDAI FM", "103.5", "NAROK", linksFromTxt.get(83), 83));
        radioStationsList.add(new RadioStation(R.drawable.radioneemalogo, "RADIO NEEMA", "--.--", "NAIROBI", linksFromTxt.get(84), 84));
        radioStationsList.add(new RadioStation(R.drawable.radiosimbalogo, "RADIO SIMBA", "93.1", "BUNGOMA", linksFromTxt.get(85), 85));
        radioStationsList.add(new RadioStation(R.drawable.westfmlogo, "WEST FM", "94.9", "NAIROBI", linksFromTxt.get(86), 86));
        radioStationsList.add(new RadioStation(R.drawable.iftinfmlogo, "IFTIIN FM", "101.9", "NAIROBI", linksFromTxt.get(87), 87));
        radioStationsList.add(new RadioStation(R.drawable.mintofmlogo, "MINTO FM", "101.7", "NAIROBI", linksFromTxt.get(88), 88));
        radioStationsList.add(new RadioStation(R.drawable.bikapkoretradiologo, "BIKAPKORET RADIO", "98.2", "ELDORET", linksFromTxt.get(89), 89));
        radioStationsList.add(new RadioStation(R.drawable.muratafmlogo, "MURATA FM", "98.2", "ELDORET", linksFromTxt.get(90), 90));
        radioStationsList.add(new RadioStation(R.drawable.campusradiologo, "CAMPUS RADIO", "--.--", "NAIROBI", linksFromTxt.get(91), 91));
        radioStationsList.add(new RadioStation(R.drawable.utheriradiologo, "UTHERI RADIO", "106.2", "NAIROBI", linksFromTxt.get(93), 93));
        radioStationsList.add(new RadioStation(R.drawable.mugambowamugikuyulogo, "MUGAMBO WA MUGIKUYU", "--.--", "NAIROBI", linksFromTxt.get(94), 94));
        radioStationsList.add(new RadioStation(R.drawable.mwakifmlogo, "MWAKI FM", "--.--", "NAIROBI", linksFromTxt.get(95), 95));
        radioStationsList.add(new RadioStation(R.drawable.shilohradiologo, "SHILOH RADIO", "--.--", "NAIROBI", linksFromTxt.get(96), 96));
        radioStationsList.add(new RadioStation(R.drawable.cambrigeradiologo, "CAMBRIDGE RADIO", "--.--", "NAIROBI", linksFromTxt.get(97), 97));
        radioStationsList.add(new RadioStation(R.drawable.angeladventistlogo, "ADVENTIST ANGELS", "90.0", "KISII", linksFromTxt.get(98), 98));
        radioStationsList.add(new RadioStation(R.drawable.utuuroradiologo, "UTUURO RADIO", "--.--", "NAIROBI", linksFromTxt.get(99), 99));
        radioStationsList.add(new RadioStation(R.drawable.maneneradiologo, "MANENE RADIO", "--.--", "KISUMU", linksFromTxt.get(100), 100));
        radioStationsList.add(new RadioStation(R.drawable.congasisfmlogo, "CONG'ASIS FM", "88.9", "NAIROBI", linksFromTxt.get(101), 101));
        radioStationsList.add(new RadioStation(R.drawable.northriftradiologo, "NORTHRIFT RADIO", "104.5", "KAPENGURIA", linksFromTxt.get(102), 102));
        radioStationsList.add(new RadioStation(R.drawable.relaxradiologo, "RELAX RADIO", "103.0", "NAIROBI", linksFromTxt.get(103), 103));
        radioStationsList.add(new RadioStation(R.drawable.smashjamradiologo, "SMASH JAM RADIO", "99.0", "NAKURU", linksFromTxt.get(104), 104));
        radioStationsList.add(new RadioStation(R.drawable.getufmlogo, "GETU RADIO", "87.6", "MERU", linksFromTxt.get(105), 105));
        radioStationsList.add(new RadioStation(R.drawable.kokwofmlogo, "KOKWO FM", "100.1", "KAPENGURIA", linksFromTxt.get(106), 106));
        radioStationsList.add(new RadioStation(R.drawable.mumbofmlogo, "MUMBO FM", "90.2", "BUNGOMA", linksFromTxt.get(107), 107));
        radioStationsList.add(new RadioStation(R.drawable.tandazafmlogo, "TANDAZA FM", "103.7", "BUNGOMA", linksFromTxt.get(108), 108));
        radioStationsList.add(new RadioStation(R.drawable.mixxradio560logo, "560 MIXX RADIO", "560kHz", "NAIROBI", linksFromTxt.get(109), 109));
        radioStationsList.add(new RadioStation(R.drawable.christianradiologo560, "560 CHRISTIAN RADIO", "560kHz", "NAIROBI", linksFromTxt.get(110), 110));
        radioStationsList.add(new RadioStation(R.drawable.powercountrylogo560, "560 POWER COUNTRY", "560kHz", "NAIROBI", linksFromTxt.get(111), 111));
        radioStationsList.add(new RadioStation(R.drawable.muthingifmlogo, "MUTHINGI FM", "--.--", "NAIROBI", linksFromTxt.get(225), 225));
        radioStationsList.add(new RadioStation(R.drawable.centralfmlogo, "CENTRAL FM", "97.1", "NANYUKI", linksFromTxt.get(112), 112));
        radioStationsList.add(new RadioStation(R.drawable.mayiengaradiologo, "MAYIENGA FM", "93.5", "BUNGOMA", linksFromTxt.get(113), 113));
        radioStationsList.add(new RadioStation(R.drawable.kitwekradiologo, "KITWEK RADIO", "92.9", "NAIROBI", linksFromTxt.get(114), 114));
        radioStationsList.add(new RadioStation(R.drawable.mwaturadiologo, "MWATU RADIO", "93.1", "MACHAKOS", linksFromTxt.get(115), 115));
        radioStationsList.add(new RadioStation(R.drawable.ingofmlogo, "INGO FM", "100.5", "NAIROBI", linksFromTxt.get(116), 116));
        radioStationsList.add(new RadioStation(R.drawable.powerfmlogo, "POWER FM", "--.--", "NAIROBI", linksFromTxt.get(117), 117));
        radioStationsList.add(new RadioStation(R.drawable.makinikaradiologo, "MAKINIKA RADIO", "--.--", "NAIROBI", linksFromTxt.get(118), 118));
        radioStationsList.add(new RadioStation(R.drawable.bloomradiologo, "BLOOM RADIO", "--.--", "NAIROBI", linksFromTxt.get(119), 119));
        radioStationsList.add(new RadioStation(R.drawable.rainbowradiologo, "RAINBOW RADIO", "---.--", "NAIROBI", linksFromTxt.get(120), 120));
        radioStationsList.add(new RadioStation(R.drawable.togotanefmlogo, "TOGOTANE FM", "88.3", "NAIROBI", linksFromTxt.get(121), 121));
        radioStationsList.add(new RadioStation(R.drawable.eretofmlogo, "ERETO FM", "--.--", "NYERI", linksFromTxt.get(122), 122));
        radioStationsList.add(new RadioStation(R.drawable.midzifmlogo, "MIDZI FM", "100.5", "MALINDI", linksFromTxt.get(123), 123));
        radioStationsList.add(new RadioStation(R.drawable.radio44logo, "RADIO 44", "91.6", "NAIROBI", linksFromTxt.get(124), 124));
        radioStationsList.add(new RadioStation(R.drawable.sayakiradiologo, "SAYAKI RADIO", "--.--", "NYERI", linksFromTxt.get(125), 125));
        radioStationsList.add(new RadioStation(R.drawable.radiosafarilogo, "RADIO SAFARI", "87.9", "KITALE", linksFromTxt.get(126), 126));

        radioStationsList.add(new RadioStation(R.drawable.berurfmlogo, "BERUR FM", "96.7", "NAIROBI", linksFromTxt.get(227), 227));
        radioStationsList.add(new RadioStation(R.drawable.radiofahamulogo, "RADIO FAHAMU", "--.--", "NAIROBI", linksFromTxt.get(127), 127));
        // radioStationsList.add(new RadioStation(R.drawable.mecolfmlogo, "MECOL FM", "--.--", "NAIROBI", linksFromTxt.get(128)));
        radioStationsList.add(new RadioStation(R.drawable.radio254logo, "RADIO 254", "--.--", "NAIROBI", linksFromTxt.get(129), 129));
        radioStationsList.add(new RadioStation(R.drawable.varchradiologo, "VARCH RADIO", "--.--", "ELDORET", linksFromTxt.get(130), 130));
        radioStationsList.add(new RadioStation(R.drawable.radiovunalogo, "RADIO VUNA", "102.0", "KISII", linksFromTxt.get(205), 205));
        radioStationsList.add(new RadioStation(R.drawable.tonziradiologo, "TONZI RADIO", "--.--", "NAIROBI", linksFromTxt.get(131), 131));
        radioStationsList.add(new RadioStation(R.drawable.ifmlogo, "IFM PARTY STATION", "--.--", "NAIROBI", linksFromTxt.get(132), 132));
        radioStationsList.add(new RadioStation(R.drawable.elwaicenterfmlogo, "ELWAI CENTER FM", "--.--", "SUSWA", linksFromTxt.get(133), 133));
        radioStationsList.add(new RadioStation(R.drawable.countrypridefmlogo, "COUNTRY PRIDE FM", "--.--", "NAIROBI", linksFromTxt.get(134), 134));
        radioStationsList.add(new RadioStation(R.drawable.musicjunkieslogo, "MUSICJUNKIES FM", "--.--", "NAIROBI", linksFromTxt.get(135), 135));
        radioStationsList.add(new RadioStation(R.drawable.smoothjazz560logo, "560 SMOOTHJAZZ", "--.--", "NAIROBI", linksFromTxt.get(136), 136));
        radioStationsList.add(new RadioStation(R.drawable.lightfmlogo, "LIGHT FM", "--.--", "NAIROBI", linksFromTxt.get(137), 137));
        radioStationsList.add(new RadioStation(R.drawable.icedfmlogo, "ICED RADIO", "--.--", "NAIROBI", linksFromTxt.get(138), 138));
        radioStationsList.add(new RadioStation(R.drawable.geeradiologo, "GEE RADIO", "--.--", "NAIROBI", linksFromTxt.get(139), 139));
        radioStationsList.add(new RadioStation(R.drawable.tulwoobkoonyradiologo, "TULWOOBKOONY RADIO", "--.--", "NAIROBI", linksFromTxt.get(140), 140));
        radioStationsList.add(new RadioStation(R.drawable.hootersfmlogo, "HOOTERS FM", "--.--", "NAIROBI", linksFromTxt.get(141), 141));
        radioStationsList.add(new RadioStation(R.drawable.kufurahiafmlogo, "KAFURAHA FM", "--.--", "NAIROBI", linksFromTxt.get(142), 142));
        radioStationsList.add(new RadioStation(R.drawable.hoodradiologo, "HOOD RADIO", "--.--", "NAIROBI", linksFromTxt.get(143), 143));
        radioStationsList.add(new RadioStation(R.drawable.abundanceradio, "ABUNDANCE RADIO", "--.--", "NAIROBI", linksFromTxt.get(144), 144));
        radioStationsList.add(new RadioStation(R.drawable.aipcalogo, "AIPCA RADIO", "--.--", "NAIROBI", linksFromTxt.get(145), 145));
        radioStationsList.add(new RadioStation(R.drawable.msenangufmlogo, "MSENANGU FM", "99.5", "MOMBASA", linksFromTxt.get(146), 146));
        radioStationsList.add(new RadioStation(R.drawable.fynradiologo, "FYN RADIO", "--.--", "NAIROBI", linksFromTxt.get(147), 147));
        radioStationsList.add(new RadioStation(R.drawable.xaticfmlogo, "XATIC FM", "--.--", "NAIROBI", linksFromTxt.get(148), 148));
        radioStationsList.add(new RadioStation(R.drawable.tusmofmlogo, "TUSMO FM", "--.--", "NAIROBI", linksFromTxt.get(149), 149));
        radioStationsList.add(new RadioStation(R.drawable.radioshahidilogo, "RADIO SHAHIDI", "91.7", "ISIOLO", linksFromTxt.get(150), 150));
        radioStationsList.add(new RadioStation(R.drawable.pemiradiologo, "PEMI RADIO", "96.1", "NAIROBI", linksFromTxt.get(151), 151));
        radioStationsList.add(new RadioStation(R.drawable.freshfmlogo, "FRESH FM", "--.--", "NAIROBI", linksFromTxt.get(152), 152));
        radioStationsList.add(new RadioStation(R.drawable.hypemagnetradiologo, "HYPEMAGNET RADIO", "--.--", "NAIROBI", linksFromTxt.get(153), 153));
        radioStationsList.add(new RadioStation(R.drawable.mindimoradiologo, "MIDNIMO RADIO", "--.--", "NAIROBI", linksFromTxt.get(154), 154));
        radioStationsList.add(new RadioStation(R.drawable.preachgospelradiologo, "PREACHGOSPEL", "--.--", "KISUMU", linksFromTxt.get(155), 155));
        radioStationsList.add(new RadioStation(R.drawable.radiomikayilogo, "RADIO MIKAYI", "88.8", "HOMABAY", linksFromTxt.get(156), 156));
        radioStationsList.add(new RadioStation(R.drawable.konyonfmlogo, "KONYON FM", "--.--", "KERICHO", linksFromTxt.get(157), 157));
        radioStationsList.add(new RadioStation(R.drawable.pgradiologo, "PG RADIO", "--.--", "KISUMU", linksFromTxt.get(158), 158));
        radioStationsList.add(new RadioStation(R.drawable.aluochrisradiologo, "ALUOCHRIS RADIO", "--.--", "KISUMU", linksFromTxt.get(159), 159));
        radioStationsList.add(new RadioStation(R.drawable.doitordoitfmlogo, "DOITORDOIT", "--.--", "MOMBASA", linksFromTxt.get(160), 160));
        radioStationsList.add(new RadioStation(R.drawable.doctorsexplainlogo, "DOCTORS EXPLAIN", "--.--", "NAIROBI", linksFromTxt.get(161), 161));
        radioStationsList.add(new RadioStation(R.drawable.edenmediaradiologo, "EDEN MEDIA", "--.--", "NAIROBI", linksFromTxt.get(162), 162));
        radioStationsList.add(new RadioStation(R.drawable.xpressradiologo, "XPRESS RADIO", "102.9", "NAIROBI", linksFromTxt.get(163), 163));
        radioStationsList.add(new RadioStation(R.drawable.rasstyleradiologo, "RASSTYLE RADIO", "--.--", "NAIROBI", linksFromTxt.get(164), 164));
        radioStationsList.add(new RadioStation(R.drawable.optimumradiologo, "OPTIMUM RADIO", "--.--", "NAIROBI", linksFromTxt.get(165), 165));
        radioStationsList.add(new RadioStation(R.drawable.radiobarazalogo, "RADIO BARAZA", "--.--", "NAIROBI", linksFromTxt.get(166), 166));
        radioStationsList.add(new RadioStation(R.drawable.radiopunchline, "RADIO PUNCHLINE", "--.--", "NAIROBI", linksFromTxt.get(167), 167));
        radioStationsList.add(new RadioStation(R.drawable.gituambafmlogo, "GITUAMBA FM", "98.2MHz", "NAIROBI", linksFromTxt.get(168), 168));
        radioStationsList.add(new RadioStation(R.drawable.daradiologo, "DA RADIO", "--.--", "NAIROBI", linksFromTxt.get(169), 169));
        radioStationsList.add(new RadioStation(R.drawable.uncutradiologo, "UNCUT RADIO", "--.--", "NAIROBI", linksFromTxt.get(170), 170));
        radioStationsList.add(new RadioStation(R.drawable.gracefmlogo, "GRACE FM", "--.--", "NAIROBI", linksFromTxt.get(171), 171));
        radioStationsList.add(new RadioStation(R.drawable.radiochichilogo, "RADIO CHICHI", "100.7", "BARINGO", linksFromTxt.get(202), 202));
        radioStationsList.add(new RadioStation(R.drawable.theophilusfmlogo, "THEOPHILUS FM", "--.--", "NAIROBI", linksFromTxt.get(221), 221));
        radioStationsList.add(new RadioStation(R.drawable.ebruradiologo, "EBRU RADIO", "89.7", "MANDERA", linksFromTxt.get(222), 222));
        radioStationsList.add(new RadioStation(R.drawable.vybesradiologo, "VYBES RADIO", "104.5", "NAIROBI", linksFromTxt.get(172), 172));
        radioStationsList.add(new RadioStation(R.drawable.starfmlogo, "STAR FM", "105.9", "NAIROBI", linksFromTxt.get(173), 173));
        radioStationsList.add(new RadioStation(R.drawable.mwangazawanenofmlogo, "MWANGAZA WA NENO FM", "--.--", "NAIROBI", linksFromTxt.get(174), 174));
        radioStationsList.add(new RadioStation(R.drawable.thequestradiologo, "THEQUEST RADIO", "--.--", "NAIROBI", linksFromTxt.get(175), 175));
        radioStationsList.add(new RadioStation(R.drawable.radioasuronlogo, "RADIO ASURON", "--.--", "NAIROBI", linksFromTxt.get(176), 176));
        radioStationsList.add(new RadioStation(R.drawable.ruimbofmlogo, "RUIMBO FM", "--.--", "NAIROBI", linksFromTxt.get(177), 177));
        radioStationsList.add(new RadioStation(R.drawable.safinaradiologo, "SAFINA RADIO", "--.--", "NAIROBI", linksFromTxt.get(178), 178));
        radioStationsList.add(new RadioStation(R.drawable.sayarefmlogo, "SAYARE FM", "--.--", "NAIROBI", linksFromTxt.get(179), 179));
        radioStationsList.add(new RadioStation(R.drawable.prasieradiologo, "PRAISE RADIO", "--.--", "NAIROBI", linksFromTxt.get(180), 180));
        radioStationsList.add(new RadioStation(R.drawable.tognofm, "TOGNO FM", "--.--", "NAIROBI", linksFromTxt.get(181), 181));
        radioStationsList.add(new RadioStation(R.drawable.moronigospellogo, "MORONI GOSPEL", "--.--", "NAIROBI", linksFromTxt.get(182), 182));
        radioStationsList.add(new RadioStation(R.drawable.mwanaspotifmlogo, "MWANASPORTI FM", "--.--", "NAIROBI", linksFromTxt.get(183), 183));
        radioStationsList.add(new RadioStation(R.drawable.touchradiologo, "TOUCH RADIO", "--.--", "NAIROBI", linksFromTxt.get(184), 184));
        radioStationsList.add(new RadioStation(R.drawable.uigithaniofmlogo, "UIGITHANIO FM", "--.--", "NAIROBI", linksFromTxt.get(185), 185));
        radioStationsList.add(new RadioStation(R.drawable.radioamanilogo, "RADIO AMANI", "--.--", "NAIROBI", linksFromTxt.get(186), 186));
        radioStationsList.add(new RadioStation(R.drawable.ksmradiologo, "KSM RADIO", "--.--", "NAIROBI", linksFromTxt.get(187), 187));
        radioStationsList.add(new RadioStation(R.drawable.dapstreamradiologo, "DAPSTREAM RADIO", "--.--", "NAIROBI", linksFromTxt.get(188), 188));
        radioStationsList.add(new RadioStation(R.drawable.engelosradiologo, "ENGELOS RADIO", "--.--", "NAIROBI", linksFromTxt.get(189), 189));
        radioStationsList.add(new RadioStation(R.drawable.semafmlogo, "SEMA FM", "98.3", "NAIROBI", linksFromTxt.get(190), 190));
        radioStationsList.add(new RadioStation(R.drawable.kochfmlogo, "KOCH FM", "99.9", "NAIROBI", linksFromTxt.get(191), 191));
        radioStationsList.add(new RadioStation(R.drawable.tsfmlogo, "TS FM", "--.--", "NAIROBI", linksFromTxt.get(192), 192));
        radioStationsList.add(new RadioStation(R.drawable.nworksradiologo, "NWORKS RADIO", "--.--", "NAIROBI", linksFromTxt.get(193), 193));
        radioStationsList.add(new RadioStation(R.drawable.serianfmlogo, "SERIAN FM", "--.--", "NAIROBI", linksFromTxt.get(194), 194));
        radioStationsList.add(new RadioStation(R.drawable.radiomlimalogo, "RADIO MLIMA", "--.--", "NAIROBI", linksFromTxt.get(195), 195));
        radioStationsList.add(new RadioStation(R.drawable.ggvfmlogo, "GGV FM", "--.--", "NAIROBI", linksFromTxt.get(196), 196));
        radioStationsList.add(new RadioStation(R.drawable.popoteradiologo, "POPOTE RADIO", "--.--", "NAIROBI", linksFromTxt.get(197), 197));
        radioStationsList.add(new RadioStation(R.drawable.radiotumainilogo, "RADIO TUMAINI", "93.0", "NAIROBI", linksFromTxt.get(198), 198));
        radioStationsList.add(new RadioStation(R.drawable.badidearadiologo, "BAD IDEA RADIO", "--.--", "NAIROBI", linksFromTxt.get(199), 199));
        radioStationsList.add(new RadioStation(R.drawable.diplomatradiologo, "DIPLOMAT RADIO", "--.--", "NAIROBI", linksFromTxt.get(200), 200));
        radioStationsList.add(new RadioStation(R.drawable.habeshingamusiclogo, "HABESHINGA MUSIC", "--.--", "NAIROBI", linksFromTxt.get(201), 201));


    }

    void txtToArrayList() {
        try {
            linksFromTxt.clear();
            File file = new File(getFilesDir(), "file.txt");
            BufferedReader reader = new BufferedReader(new FileReader(file));
            BufferedReader br = new BufferedReader(reader);

            for (int index = 0; index < 231; index++) {
                linksFromTxt.add(br.readLine());
            }
        } catch (IOException e) {
            e.printStackTrace();
            //Toast.makeText(MainActivity.this, "ioexcept", Toast.LENGTH_SHORT).show();
        }
    }

    void txtToArrayListAfterUpdate() {
        try {
            linksAfterUpdate.clear();
            File file = new File(getFilesDir(), "file.txt");
            BufferedReader reader = new BufferedReader(new FileReader(file));
            BufferedReader br = new BufferedReader(reader);
            for (int index = 0; index < 231; index++) {
                linksAfterUpdate.add(br.readLine());
            }
            //Toast.makeText(MainActivity.this, "file read succeful", Toast.LENGTH_SHORT).show()
        } catch (IOException e) {
            e.printStackTrace();
            //Toast.makeText(MainActivity.this, "ioexcept", Toast.LENGTH_SHORT).show();
        }
    }

    void createInitialTxt() {
        File file = new File(getFilesDir(), "file.txt");
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(file));
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            writer.write("http://freeuk28.listen2myradio.com:15203/;stream.nsv");//0
            writer.newLine();
            writer.write("http://stream.group8.africa:44001/soundcityfmnrb");//1
            writer.newLine();
            writer.write("https://streamingv2.shoutcast.com/kiss100");//2
            writer.newLine();
            writer.write("https://streamingv2.shoutcast.com/nrg-radio-ke?ver=388771");//3
            writer.newLine();
            writer.write("https://61115b0a477b5.streamlock.net:8443/inoorofm/inoorofm/playlist.m3u8");//4
            writer.newLine();
            writer.write("https://streamingv2.shoutcast.com/choice-radio-ke?ver=69944");//5
            writer.newLine();
            writer.write("https://radio.citizentv.co.ke/hot96fm/hot96fm/playlist.m3u8");//6
            writer.newLine();
            writer.write("http://91.121.165.88:8116/stream?1473424110680.mp3");//7
            writer.newLine();
            writer.write("https://streamingv2.shoutcast.com/classic105fm");//8
            writer.newLine();
            writer.write("https://streamingv2.shoutcast.com/radio-jambo");//9
            writer.newLine();
            writer.write("http://node-24.zeno.fm/xv5375hfkbruv?rj-ttl=5&rj-tok=AAABeNzocwIAxY_3rNKbGJDvsA");//10
            writer.newLine();
            writer.write("http://node-28.zeno.fm/frsvy955puquv?rj-ttl=5&rj-tok=AAABeMfmU3oAxn3lHLAgD8YVfA");//11
            writer.newLine();
            writer.write("http://node-17.zeno.fm/kvudezx1h2zuv?rj-ttl=5&rj-tok=AAABeMIsGNYAVfoPd-vMjfAVWw");//12
            writer.newLine();
            writer.write("https://radiomaisha-atunwadigital.streamguys1.com/radiomaisha?aw_0_1st.playerid=SGplayer&aw_0_1st.skey=1618649256530&awparams=playerid%3ASGplayer%3B&aw_0_req.gdpr=true&us_privacy=1YNN&aw_0_req.gdpr=true&us_privacy=1YNN");//13
            writer.newLine();
            writer.write("http://node-26.zeno.fm/wvw02zaqpxquv?rj-ttl=5&rj-tok=AAABeaAPtgoA9vudLgmjCcfzOA");//14
            writer.newLine();
            writer.write("https://61115b0a477b5.streamlock.net:8443/ramogifm/ramogifm/playlist.m3u8");//15
            writer.newLine();
            writer.write("https://61115b0a477b5.streamlock.net:8443/egesafm/egesafm/playlist.m3u8");//16
            writer.newLine();
            writer.write("https://61115b0a477b5.streamlock.net:8443/mulembefm/mulembefm/playlist.m3u8");//17
            writer.newLine();
            writer.write("https://61115b0a477b5.streamlock.net:8443/musyifm/musyifm/playlist.m3u8");///18
            writer.newLine();
            writer.write("https://61115b0a477b5.streamlock.net:8443/chamgeifm/chamgeifm/playlist.m3u8");//19
            writer.newLine();
            writer.write("https://61115b0a477b5.streamlock.net:8443/muugafm/muugafm/playlist.m3u8");//20
            writer.newLine();
            writer.write("https://61115b0a477b5.streamlock.net:8443/sulwefm/sulwefm/playlist.m3u8");//21
            writer.newLine();
            writer.write("https://61115b0a477b5.streamlock.net:8443/wimwarofm/wimwarofm/playlist.m3u8");//21
            writer.newLine();
            writer.write("https://61115b0a477b5.streamlock.net:8443/baharifm/baharifm/playlist.m3u8");//23
            writer.newLine();
            writer.write("https://61115b0a477b5.streamlock.net:8443/vuukafm/vuukafm/playlist.m3u8");//24
            writer.newLine();
            writer.write("https://s3.radio.co/sa8bd65b03/listen");//25
            writer.newLine();
            writer.write("https://c32.radioboss.fm:18097/stream");//26
            writer.newLine();
            writer.write("http://uk1-vn.mixstream.net:10104/stream/1/");//27
            writer.newLine();
            writer.write("https://listen-familymedia.sharp-stream.com/familymedia.mp3");//28
            writer.newLine();
            writer.write("https://s3.radio.co/s97f38db97/listen?1618612726568");//29
            writer.newLine();
            writer.write("http://n08.radiojar.com/u02dd8buzv8uv?rj-ttl=5&rj-tok=AAABegdH1ukARHjg7wj_K686hQ");//30
            writer.newLine();
            writer.write("https://streamingv2.shoutcast.com/smooth-1055");//31
            writer.newLine();
            writer.write("https://us4.internet-radio.com/proxy/kigoocofm?mp=/stream");//32
            writer.newLine();
            writer.write("http://node-19.zeno.fm/5kg3170f3qzuv?rj-ttl=5&rj-tok=AAABePV2z0YA6zJMSNE7VIdKXQ");//33
            writer.newLine();
            writer.write("http://197.232.43.22:88/broadwave.mp3");//34
            writer.newLine();
            writer.write("http://node-33.zeno.fm/smhmfr1a94zuv?rj-ttl=5&rj-tok=AAABePV-LbMApra6NcQ4i6_NxA");//35
            writer.newLine();
            writer.write("https://s37.myradiostream.com/:11968/stream/1/");//36
            writer.newLine();
            writer.write("http://node-34.zeno.fm/4fdwx9mydbruv?rj-ttl=5&rj-tok=AAABePV-uaMACyVbjXvW3Gnnng");//37
            writer.newLine();
            writer.write("https://s2.radio.co/sa399cbc39/listen");//38
            writer.newLine();
            writer.write("http://5.39.89.17:14562/;?0.86572102645080950");//39
            writer.newLine();
            writer.write("https://streamingv2.shoutcast.com/east-fm");//40
            writer.newLine();
            writer.write("https://streamingv2.shoutcast.com/gukena-fm");//41
            writer.newLine();
            writer.write("http://media.kassfm.co.ke:8006/live");//42
            writer.newLine();
            writer.write("http://41.72.210.222:88/stream?1618607493122");//43
            writer.newLine();
            writer.write("http://n11.radiojar.com/3by7s8eg65quv?rj-ttl=5&rj-tok=AAABeNyS1HgAMfiNnuaHIr-0-g");//44
            writer.newLine();
            writer.write("http://node-32.zeno.fm/gvk894g072quv?rj-ttl=5&rj-tok=AAABeNyWbl8Ak6RooIBikh7BRg");//45
            writer.newLine();
            writer.write("http://uk1-vn.mixstream.net:10108/;stream.mp3");//46
            writer.newLine();
            writer.write("http://node-15.zeno.fm/mbpgmuumubruv?rj-ttl=5&rj-tok=AAABeNyb9IsACHEHHIsAW4qPww");//47
            writer.newLine();
            writer.write("http://node-20.zeno.fm/dex4fk5ykxquv?rj-ttl=5&rj-tok=AAABeNyd9awAOZSgSHLHsFOzSQ");//48
            writer.newLine();
            writer.write("https://usa9-vn.mixstream.net/8084/listen.mp3");//49
            writer.newLine();
            writer.write("https://spicefm-atunwadigital.streamguys1.com/spicefm?aw_0_1st.playerid=SGplayer&aw_0_1st.skey=1618609821672&awparams=playerid%3ASGplayer%3B&us_privacy=1YNN&us_privacy=1YNN&aw_0_req.gdpr=true&us_privacy=1YNN");//50
            writer.newLine();
            writer.write("http://s2.myradiostream.com:5788/;stream.nsv");//51
            writer.newLine();
            writer.write("http://node-12.zeno.fm/8d58094z4neuv?rj-ttl=5&rj-tok=AAABeNzD6GQA0EEZxXHqLW0ofQ");//52
            writer.newLine();
            writer.write("http://135.181.78.169:8802/stream/");//53
            writer.newLine();
            writer.write("https://atunwadigital.streamguys1.com/capitalfm");//54
            writer.newLine();
            writer.write("https://beatlock-radiohosting.radioca.st/1");//55
            writer.newLine();
            writer.write("http://node-12.zeno.fm/2kcm5f3mga0uv.aac?rj-ttl=5&rj-tok=AAABeaBnIvQARefbFHMp6jVEtw");//56
            writer.newLine();
            writer.write("http://node-20.zeno.fm/2188qutw5k8uv.aac?rj-ttl=5&rj-tok=AAABeaBpKEQAqC_c47Fwkst01Q");//57
            writer.newLine();
            writer.write("http://node-14.zeno.fm/2t5rx36g7zquv.aac?rj-ttl=5&rj-tok=AAABeaBpq4QAnJHfMKZgDP-exg");//58
            writer.newLine();
            writer.write("http://node-24.zeno.fm/q8rswhhs8mruv?rj-ttl=5&rj-tok=AAABeaBq6dwAg74JrDpCFAiz4w");//59
            writer.newLine();
            writer.write("http://node-16.zeno.fm/5bfp00w0s4zuv?rj-ttl=5&rj-tok=AAABeNzNJfAAoGpIOrZCnx0wtQ");//60
            writer.newLine();
            writer.write("https://azura12.instainternet.com/radio/8010/radio.mp3?1618611692");//61
            writer.newLine();
            writer.write("http://node-21.zeno.fm/215bpympks8uv?rj-ttl=5&rj-tok=AAABeNzTUE0A4friDnL_es2JwA");//62
            writer.newLine();
            writer.write("http://node-23.zeno.fm/w5gg57a5v2quv?rj-ttl=5&rj-tok=AAABeNzXvbwApuKqlDDFdj0W9A");//63
            writer.newLine();
            writer.write("http://node-28.zeno.fm/n0adr084v2quv?rj-ttl=5&rj-tok=AAABeNzaZNkAwn73Y0de3WnglA");//64
            writer.newLine();
            writer.write("http://node-23.zeno.fm/femq5wg5v2quv?rj-ttl=5&rj-tok=AAABeNzaZjoA5mn6z8Gydbp6HA");//65
            writer.newLine();
            writer.write("http://node-18.zeno.fm/m9dx0394v2quv?rj-ttl=5&rj-tok=AAABeNzaZ-8AkLHMucNAqkbjWA");//66
            writer.newLine();
            writer.write("http://s3.myradiostream.com:4560/");//67
            writer.newLine();
            writer.write("http://node-07.zeno.fm/308mfq5yc5zuv?rj-ttl=5&rj-tok=AAABeSSTVBIAI4hlFBDMfZPvPA");//68
            writer.newLine();
            writer.write("https://s3.radio.co/s31150ae36/listen");//69
            writer.newLine();
            writer.write("http://node-29.zeno.fm/mhmwnyyst5quv?rj-ttl=5&rj-tok=AAABeNzs6Z8ALkR7qxnPrOoFIQ");//70
            writer.newLine();
            writer.write("http://201.netromedia.com/MbaituFM001/MbaituFM001/playlist.m3u8");//71
            writer.newLine();
            writer.write("https://dreamsiteradiocp5.com/proxy/rmkenyanai?mp=/stream");//72
            writer.newLine();
            writer.write("http://node-22.zeno.fm/pbzn43z1mkeuv?rj-ttl=5&rj-tok=AAABebjgWIoA3vlbh4UEUJAAFA");//73
            writer.newLine();
            writer.write("http://node-21.zeno.fm/m25b7vevgm8uv?rj-ttl=5&rj-tok=AAABebkciQkAM3fhj_q2pjWS_w");//74
            writer.newLine();
            writer.write("http://node-33.zeno.fm/k6wxk944rs8uv?rj-ttl=5&rj-tok=AAABebkekBkAljEyqQsTEB_S8w");//75
            writer.newLine();
            writer.write("http://uk4-vn.webcast-server.net:8146/stream");//76
            writer.newLine();
            writer.write("https://node-30.zeno.fm/k7ucfn1yyv8uv?rj-ttl=5&rj-tok=AAABelVF8fIA6uwinpftOYIZ4Q");//77
            writer.newLine();
            writer.write("http://node-03.zeno.fm/9sw1dvaeh5zuv?rj-ttl=5&rj-tok=AAABelVQQ5IADfTvsxuhimarlw");//78
            writer.newLine();
            writer.write("https://rfm99.out.airtime.pro/rfm99_a");//79
            writer.newLine();
            writer.write("https://s43.myradiostream.com/:22826/;?type=http&nocache=1624928324");//80
            writer.newLine();
            writer.write("https://audio-edge-t85at.fra.h.radiomast.io/b0528495-7854-4ea6-977b-12842b1d3c23");//81
            writer.newLine();
            writer.write("http://196.202.194.147:8090/");//82
            writer.newLine();
            writer.write("http://197.248.25.27:1234/");//83
            writer.newLine();
            writer.write("http://node-26.zeno.fm/qsrhvzs8z0duv?1618649892087=&rj-tok=AAABeN8WvGIA_jf0UuBRh98jaw&rj-ttl=5");//84
            writer.newLine();
            writer.write("http://node-31.zeno.fm/7nukpsgq3bruv?rj-ttl=5&rj-tok=AAABeR9he2QAVbkpFdyALu7qsQ");//85
            writer.newLine();
            writer.write("https://uk4-vn.mixstream.net/:8090/stream/1/");//86
            writer.newLine();
            writer.write("http://node-34.zeno.fm/1v7yr8499yzuv?rj-ttl=5&rj-tok=AAABeQmT8gMA5WZ0TTsyeyy9XA");//87
            writer.newLine();
            writer.write("http://node-05.zeno.fm/u3dw22zst5quv?rj-ttl=5&rj-tok=AAABeSIAfNAAkmiO5rJjYfDt3Q");//88
            writer.newLine();
            writer.write("http://184.154.43.106:8176/stream");//89
            writer.newLine();
            writer.write("http://node-18.zeno.fm/qw9zzkrfvg0uv?rj-ttl=5&rj-tok=AAABeZ_rUVoAGeQjSCZm1xZAHg");//90
            writer.newLine();
            writer.write("http://node-09.zeno.fm/mayn36v1v8quv.aac?rj-ttl=5&rj-tok=AAABeZ_vvnkA1tZdlhW5Viet_w");//91
            writer.newLine();
            writer.write("http://41.90.240.222:88/broadwave.mp3?src=1&rate=1&ref=\"))");
            writer.newLine();
            writer.write("http://node-13.zeno.fm/s9ya9bbapa0uv?rj-ttl=5&rj-tok=AAABeZ_zwg4A_E0C1E0iuIXR4A");
            writer.newLine();
            writer.write("http://node-10.zeno.fm/47e5brvztfeuv?rj-ttl=5&rj-tok=AAABeZ_3ImIAGH1C-X-8S9XtVw");
            writer.newLine();
            writer.write("http://node-07.zeno.fm/9x7dsdunknruv?rj-ttl=5&rj-tok=AAABeZ_6AuAA7LxbWjrEI3EXHQ");
            writer.newLine();
            writer.write("http://node-24.zeno.fm/xuvwff899mzuv?rj-ttl=5&rj-tok=AAABeZ_8TyYAgb0tPvrIK0zl_g");
            writer.newLine();
            writer.write("http://node-23.zeno.fm/wcp775f975quv?rj-ttl=5&rj-tok=AAABeZ__e6EAWjrOLEncXdK0cg");
            writer.newLine();
            writer.write("https://streamingv2.shoutcast.com/adventist-angels-watchman");
            writer.newLine();
            writer.write("http://node-33.zeno.fm/gghtw6fgp3quv?rj-ttl=5&rj-tok=AAABeaALFNIAuAhHkv4evBLTcg");
            writer.newLine();
            writer.write("https://servidor29-1.brlogic.com:8784/live");
            writer.newLine();
            writer.write("http://s6.myradiostream.com:46040/listen.mp3");
            writer.newLine();
            writer.write("https://stream.northriftradio.fynradio.com/");
            writer.newLine();
            writer.write("https://node-25.zeno.fm/7vpygga1ra0uv?rj-ttl=5&rj-tok=AAABeaATdloApWxS1vdTQfnzZA");
            writer.newLine();
            writer.write("http://node-11.zeno.fm/c0258bn5mceuv?rj-ttl=5&rj-tok=AAABeaAboKgAj6fcL5R2jLORMA");
            writer.newLine();
            writer.write("https://openradio.app/proxy/http://node-27.zeno.fm/21y6kygc6bruv?rj-tok=AAABcg01yYAA1w1NETLuJ5kokQ&rj-ttl=5");
            writer.newLine();
            writer.write("https://stream.kokwo.co.ke/listen.mp3");
            writer.newLine();
            writer.write("https://stream.kokwo.co.ke/listen.mp3");
            writer.newLine();
            writer.write("http://uk3-pn.mixstream.net:8392/listen.mp");
            writer.newLine();
            writer.write("https://tandaza.out.airtime.pro/tandaza_a");
            writer.newLine();
            writer.write("http://node-34.zeno.fm/ewm8r1at3a0uv?rj-ttl=5&rj-tok=AAABeaAsJLkAQZSe44cOIFITVw");
            writer.newLine();
            writer.write("http://node-31.zeno.fm/csx5u88s3a0uv?rj-ttl=5&rj-tok=AAABeaAst5IAyWd9nH_DRTAIYg");
            writer.newLine();
            writer.write("http://node-04.zeno.fm/181zrps10a0uv?rj-ttl=5&rj-tok=AAABeaAtUJEAvBEkXEoniQb7qQ");
            writer.newLine();
            writer.write("http://node-13.zeno.fm/khszfn8v0f8uv?rj-ttl=5&rj-tok=AAABeaMQDPUAU1qYj0CHBQYBLg");
            writer.newLine();
            writer.write("http://node-19.zeno.fm/ayw9hk0st5quv?rj-ttl=5&rj-tok=AAABeaAy9VwAH6uvMYB-ppTzqA");
            writer.newLine();
            writer.write("http://node-32.zeno.fm/gh67mvp8f2zuv?rj-ttl=5&rj-tok=AAABeaAzY4wAvKycbGZ0ungThw");
            writer.newLine();
            writer.write("http://node-27.zeno.fm/kawduafexa0uv?rj-ttl=5&rj-tok=AAABeaA0kBYARtYlbsc3JcB8EQ");
            writer.newLine();
            writer.write("http://node-26.zeno.fm/sxr6fxkgymzuv?rj-ttl=5&rj-tok=AAABeaA1lk8Av8UoUv9Z9mhsSA");
            writer.newLine();
            writer.write("http://node-10.zeno.fm/2kfask0gzk8uv?rj-ttl=5&rj-tok=AAABeaA7YlsAN7S-qQkdjyFPjw");
            writer.newLine();
            writer.write("http://node-13.zeno.fm/wbg2tf2gp3quv?rj-ttl=5&rj-tok=AAABeaA_UyMA10KD1UjXkWo3KQ");
            writer.newLine();
            writer.write("https://listen.radioking.com/radio/184609/stream/226666");
            writer.newLine();
            writer.write("http://node-18.zeno.fm/dzks8e3na2zuv?rj-ttl=5&rj-tok=AAABeaBKMXwAgUxrT20TG0PJtQ");
            writer.newLine();
            writer.write("http://node-33.zeno.fm/62yutdaahg8uv?rj-ttl=5&rj-tok=AAABeaBRld8ABoUHwtF7WLhhrQ");
            writer.newLine();
            writer.write("http://node-05.zeno.fm/97h8zzdgzk8uv?rj-ttl=5&rj-tok=AAABeaBSyDUAQaY7FxP5gVS-CA");
            writer.newLine();
            writer.write("http://node-25.zeno.fm/nk48y55tbq8uv?rj-ttl=5&rj-tok=AAABeaBVDqsAMIFQOgD6VUh1Cw");
            writer.newLine();
            writer.write("https://streaming.broadcastradio.com:10495/radio44");
            writer.newLine();
            writer.write("http://node-30.zeno.fm/tcskrfwqcg8uv?rj-ttl=5&rj-tok=AAABeaBcomUA8Q1j6K4xYHBOQg");
            writer.newLine();
            writer.write("https://stream.safariradio.fynradio.com/");
            writer.newLine();
            writer.write("https://servidor36-4.brlogic.com:7046/live");
            writer.newLine();
            writer.write("http://node-14.zeno.fm/52wva9g1fm0uv?rj-ttl=5&rj-tok=AAABeaBhyDMALbPUnRfCLUXoFA");
            writer.newLine();
            writer.write("https://s4.radio.co/sba1f00abf/listen");
            writer.newLine();
            writer.write("https://stream.abenbrothers.com/8006/stream");
            writer.newLine();
            writer.write("https://blaxquadradio.xyz/radio/8000/radio.mp3");
            writer.newLine();
            writer.write("http://uk3.internet-radio.com:8151/live");
            writer.newLine();
            writer.write("http://node-15.zeno.fm/wt1bsx9x4s8uv?rj-ttl=5&rj-tok=AAABeaB5ks4ALRFZ3_z6g17uSg");
            writer.newLine();
            writer.write("http://node-29.zeno.fm/xftghshymv8uv?rj-ttl=5&rj-tok=AAABebXFLnUAmbZ2X8zoLxrTXg");
            writer.newLine();
            writer.write("http://node-19.zeno.fm/tvtat3ru2a0uv?rj-ttl=5&rj-tok=AAABebXH-i8AcC-AeV4zwtwJnQ");
            writer.newLine();
            writer.write("http://node-09.zeno.fm/baw0n0xdmzzuv?rj-ttl=5&rj-tok=AAABebXbguYAv7oODbvPuf2AIQ");
            writer.newLine();
            writer.write("http://node-32.zeno.fm/xv1cvubu9c0uv?rj-ttl=5&rj-tok=AAABebXd9u4A77yAb4ms_M--jw");
            writer.newLine();
            writer.write("http://node-18.zeno.fm/f4c1ude8mg0uv?rj-ttl=5&rj-tok=AAABebXeyn4AkSv0RLGCqFQwwA");
            writer.newLine();
            writer.write("http://node-34.zeno.fm/wnm5qz4qad0uv?rj-ttl=5&rj-tok=AAABebXe7BUAI7VI4Hbqg5Vvbw");
            writer.newLine();
            writer.write("http://node-17.zeno.fm/aeygkg0x1a0uv.aac?rj-ttl=5&rj-tok=AAABebjK838AdbUXQMb5fdEJpg");
            writer.newLine();
            writer.write("http://node-25.zeno.fm/zbpnyas6ya0uv?rj-ttl=5&rj-tok=AAABebjWC-0A1ic4WwYZOfkQew");
            writer.newLine();
            writer.write("http://node-01.zeno.fm/zn5ssz54f2zuv?rj-ttl=5&rj-tok=AAABebjeZ0AA5ORuwQvTkLmfZg");
            writer.newLine();
            writer.write("http://node-16.zeno.fm/tu1kenwfb8zuv?rj-ttl=5&rj-tok=AAABebjgr-UA9r8P3WoFYCghsg");
            writer.newLine();
            writer.write("http://node-22.zeno.fm/307n6qcd5f8uv?rj-ttl=5&rj-tok=AAABebjiQrgA2mN1lELANVJ2kg");
            writer.newLine();
            writer.write("http://node-10.zeno.fm/w5gg57a5v2quv?rj-ttl=5&rj-tok=AAABebji8TcACrv9YNBW8U1ysw");
            writer.newLine();
            writer.write("https://stream.trap.fynradio.com/");
            writer.newLine();
            writer.write("http://node-26.zeno.fm/mx0zbsv93k0uv?rj-ttl=5&rj-tok=AAABebjpAosAfZKJWHZqFu5Wig");
            writer.newLine();
            writer.write("https://s5.voscast.com:10389/stream?1622305009475");
            writer.newLine();
            writer.write("http://node-29.zeno.fm/4qgq6z734k0uv?rj-ttl=5&rj-tok=AAABebj29xoAOAFhUwpVTCg7hQ");
            writer.newLine();
            writer.write("http://node-07.zeno.fm/1svudng42k8uv?rj-ttl=5&rj-tok=AAABebkB8CwACH4z-f0CCzXNkg");
            writer.newLine();
            writer.write("https://s4.radio.co/s98be8abfd/listen");
            writer.newLine();
            writer.write("http://n0e.radiojar.com/mfhbg675gwzuv?rj-ttl=5&rj-tok=AAABebkLpUAAOetOIlnIZ1Lasw");
            writer.newLine();
            writer.write("http://uk4-vn.webcast-server.net:8118/;stream.mp3");
            writer.newLine();
            writer.write("https://servidor24-2.brlogic.com:7378/live");//155
            writer.newLine();
            writer.write("http://freeuk12.listen2myradio.com:26855/");
            writer.newLine();
            writer.write("http://node-12.zeno.fm/7n6xsexs2qruv?rj-ttl=5&rj-tok=AAABegctIIEAeVDLhR06NeVeRA");
            writer.newLine();
            writer.write("http://servidor24-2.brlogic.com:7378/livehttp://servidor24-2.brlogic.com:7378/live");
            writer.newLine();
            writer.write("http://node-11.zeno.fm/c45dbm62yp8uv?rj-ttl=5&rj-tok=AAABegcu0NUARlFMHWPq4dDmCw");
            writer.newLine();
            writer.write("http://node-25.zeno.fm/eq786w638f8uv?rj-ttl=5&rj-tok=AAABegcy1woAjSF82PJSl7VW7g");
            writer.newLine();
            writer.write("http://node-16.zeno.fm/w7h7ffg9vv8uv?rj-ttl=5&rj-tok=AAABegc2sesAzm7YwgHVD4Ye6Q");
            writer.newLine();
            writer.write("http://node-08.zeno.fm/kke8zf5y25quv?rj-ttl=5&rj-tok=AAABegc3j8QAQCh7N6jnAZOfaA");
            writer.newLine();
            writer.write("http://51.255.235.165:5200/stream/");
            writer.newLine();
            writer.write("http://node-21.zeno.fm/gf58b0pt5x8uv?rj-ttl=5&rj-tok=AAABegc6FywAvnHwv35QqniKrw");
            writer.newLine();
            writer.write("http://node-20.zeno.fm/tdy23ra2x1zuv?rj-ttl=5&rj-tok=AAABegc7ClwAOcm8uS-ftVMUtw");
            writer.newLine();
            writer.write("http://n0a.radiojar.com/nkptds2wfpeuv?rj-ttl=5&rj-tok=AAABegc9p1EAwun03gRjh4Idxg");
            writer.newLine();
            writer.write("http://s2.radio.co/se4988efc3/listen");
            writer.newLine();
            writer.write("http://shaincast.caster.fm:39640/listen.mp3?authnd8a07fb3eec52cfacad960e2ed4a85c5");
            writer.newLine();
            writer.write("http://node-17.zeno.fm/chw3bmb6qtzuv?rj-ttl=5&rj-tok=AAABegdBCFcA7K7EMbDJ__suiw");
            writer.newLine();
            writer.write("http://node-07.zeno.fm/v8uz87e2dm8uv?rj-ttl=5&rj-tok=AAABegdD-UQAPfKUVjftQS5ztw");
            writer.newLine();
            writer.write("http://node-22.zeno.fm/6svuvt9271zuv?rj-ttl=5&rj-tok=AAABegdGimEAiKJq8aBgLVB-lQ");
            writer.newLine();
            writer.write("https://vybezradio-atunwadigital.streamguys1.com/vybezradio");
            writer.newLine();
            writer.write("http://node-34.zeno.fm/uxddnvfh4f8uv?rj-ttl=5&rj-tok=AAABegdP1sAAHv4ULwVUv5BQJg");
            writer.newLine();
            writer.write("http://node-12.zeno.fm/ey34xcbrmeruv?rj-ttl=5&rj-tok=AAABegdQ0r8AfHJQ6NRrA1Tr6Q");
            writer.newLine();
            writer.write("http://node-33.zeno.fm/54kntu41q5quv?rj-ttl=5&rj-tok=AAABegdUI80AXSkJUkYWZC8LBQ");
            writer.newLine();
            writer.write("http://node-05.zeno.fm/z3feqy1usg0uv?rj-ttl=5&rj-tok=AAABegdVnzEA1ax_45OEjrepeg");
            writer.newLine();
            writer.write("http://node-26.zeno.fm/fvkxfw0svtzuv?rj-ttl=5&rj-tok=AAABegdYLoAAPfVVO_-m2eORhA");
            writer.newLine();
            writer.write("http://node-04.zeno.fm/44p6c1crua0uv?rj-ttl=5&rj-tok=AAABegdYwz0A7KH0rP2IFvyH_w");
            writer.newLine();
            writer.write("http://node-09.zeno.fm/xptaa28x81zuv?rj-ttl=5&rj-tok=AAABegda2hkAtJKB0hklcshQrw");
            writer.newLine();
            writer.write("http://node-10.zeno.fm/7vf6ugs6nzzuv?rj-ttl=5&rj-tok=AAABegddxVsAB-S6ZoLSBGaa0g");
            writer.newLine();
            writer.write("http://node-17.zeno.fm/uwam414tqg8uv?rj-ttl=5&rj-tok=AAABegdgWEwAO__6LA2kQn6uFQ");
            writer.newLine();
            writer.write("http://node-24.zeno.fm/ey4xepfss0quv?rj-ttl=5&rj-tok=AAABegdfsj8ASU0q5oAGmhKQWw");
            writer.newLine();
            writer.write("http://node-19.zeno.fm/3ka1cnzkg0quv?rj-ttl=5&rj-tok=AAABegdiwmsAvjQawOTeLRFVRw");
            writer.newLine();
            writer.write("http://node-35.zeno.fm/gcntpm1dcm0uv?rj-ttl=5&rj-tok=AAABegdjjuIA9pn0tRVBBZDVvg");
            writer.newLine();
            writer.write("http://node-17.zeno.fm/a70k11169f0uv?rj-ttl=5&rj-tok=AAABegdkszUArVwQs0wzPMxj8Q");
            writer.newLine();
            writer.write("http://node-11.zeno.fm/s459bp33pzzuv?rj-ttl=5&rj-tok=AAABegdlvhMAymOkn5lrMVK78g");
            writer.newLine();
            writer.write("http://node-16.zeno.fm/37wzwkugrwzuv?rj-ttl=5&rj-tok=AAABegdpKnMAICngtn2FWc0VYg");
            writer.newLine();
            writer.write("http://node-15.zeno.fm/c0p85nypxueuv?rj-ttl=5&rj-tok=AAABegdo5hcAlod721i4V9ffPQ");
            writer.newLine();
            writer.write("http://node-03.zeno.fm/3pe2nkuaqs8uv?rj-ttl=5&rj-tok=AAABegdr5x8AqjIUaPdIbyE3pA");
            writer.newLine();
            writer.write("http://node-19.zeno.fm/8nby4derqwzuv?rj-ttl=5&rj-tok=AAABegdxTxUAFa0kSO8NqvjmEA");
            writer.newLine();
            writer.write("http://node-28.zeno.fm/2y8zavpedd0uv?rj-ttl=5&rj-tok=AAABegdzFSgAORCsBH7IOpu0oA");
            writer.newLine();
            writer.write("http://node-31.zeno.fm/0r93h9hd674tv?rj-ttl=5&rj-tok=AAABegd2RvEAr6kh7HmEeMkPMQ");
            writer.newLine();
            writer.write("http://node-17.zeno.fm/989698fgxs8uv?rj-ttl=5&rj-tok=AAABegd3U9YAI_GGHGOdXWjEhQ");
            writer.newLine();
            writer.write("http://node-12.zeno.fm/fsffesnkbm0uv?rj-ttl=5&rj-tok=AAABegd4CeQA2ig4wFNLThuMug");
            writer.newLine();
            writer.write("http://node-18.zeno.fm/vv6e2amdr2zuv?rj-ttl=5&rj-tok=AAABegd4oT8A4ndOmjKtwMoLdg");
            writer.newLine();
            writer.write("http://node-33.zeno.fm/52p001bpdv8uv?rj-ttl=5&rj-tok=AAABegd5MngA6aJAUGkYyOuJ9g");
            writer.newLine();
            writer.write("http://node-20.zeno.fm/c3wfgv9rwk8uv?rj-ttl=5&rj-tok=AAABegyJmPoAxQh8_aP0c_llRw");
            writer.newLine();
            writer.write("http://node-18.zeno.fm/nu9qfhxx9reuv?rj-ttl=5&rj-tok=AAABeg8nQ0wAQ8AX9LXVWEBrtg");
            writer.newLine();
            writer.write("http://node-24.zeno.fm/amgfmx9urg0uv?rj-ttl=5&rj-tok=AAABeg8qxJ8A5eVIuvJdAiQUpA");
            writer.newLine();
            writer.write("http://node-34.zeno.fm/76q5zp6ukm0uv?rj-ttl=5&rj-tok=AAABeg8sMV8A5oxBOyFHGCKucw");
            writer.newLine();
            writer.write("http://node-13.zeno.fm/zh462nwbcpeuv?rj-ttl=5&rj-tok=AAABeg8s5dAA_kXkdoUEPHcjFg");
            writer.newLine();
            writer.write("http://node-07.zeno.fm/3gcv3fevea0uv?rj-ttl=5&rj-tok=AAABeg8t3jEA4JQeWX4mZnXdBA");
            writer.newLine();
            writer.write("https://61115b0a477b5.streamlock.net:8443/radiocitizen/radiocitizen/playlist.m3u8");
            writer.newLine();
            writer.write("https://node-23.zeno.fm/nff1e12ee18uv?rj-ttl=5&rj-tok=AAABe2-XAuwAeB-pUmz1FVrehw");//204
            writer.newLine();
            writer.write("https://securestreams4.autopo.st:1646/stream.mp3");//205
            writer.newLine();
            writer.write("https://goliveafrica.media:9998/live/61a86bf3bf0d7/index.m3u8");//206
            writer.newLine();
            writer.write("http://stream.zeno.fm/k7unn6rdu68uv?rj");//207
            writer.newLine();
            writer.write("https://securestreams4.autopo.st:1848/stream.mp3");//208
            writer.newLine();
            writer.write("https://stream.kerioradio.fynradio.com/");//209
            writer.newLine();
            writer.write("https://stream.zeno.fm/sf8e71k61rhvv");//210
            writer.newLine();
            writer.write("https://uksoutha.streaming.broadcast.radio/nrghiphop");//211
            writer.newLine();
            writer.write("https://uksoutha.streaming.broadcast.radio/nrgdancehall");//212
            writer.newLine();
            writer.write("https://uksoutha.streaming.broadcast.radio/nrggospel");//213
            writer.newLine();
            writer.write("https://uksoutha.streaming.broadcast.radio/nrgafrobeats");//214
            writer.newLine();
            writer.write("https://uksoutha.streaming.broadcast.radio/nrgmixology");//215
            writer.newLine();
            writer.write("https://uksoutha.streaming.broadcast.radio/nrgrnb");//216
            writer.newLine();
            writer.write("https://uksoutha.streaming.broadcast.radio/nrgjazz");//217
            writer.newLine();
            writer.write("https://stream.zeno.fm/3bp7c3sfx98uv");//218
            writer.newLine();
            writer.write("https://stream.zeno.fm/nw5wm356998uv");//219
            writer.newLine();
            writer.write("https://stream.zeno.fm/bwf54lxyshpuv");//220
            writer.newLine();
            writer.write("https://stream.zeno.fm/acfn8lk5bpjvv");//221
            writer.newLine();
            writer.write("https://stream.zeno.fm/4vqa4pjsbmnvv");//222
            writer.newLine();
            writer.write("https://stream.zeno.fm/u6wh2czf8e9uv");//223
            writer.newLine();
            writer.write("stream.zeno.fm/u6wh2c");//224
            writer.newLine();
            writer.write("https://stream.zeno.fm/7w2p8zrvea0uv");//225
            writer.newLine();
            writer.write("http://stream-51.zeno.fm/720g92vfzrhvv");//226
            writer.newLine();
            writer.write("https://berurfm-atunwadigital.streamguys1.com/berurfm?aw_0_1st.playerid=SGplayer&aw_0_1st.skey=1686644247597&awparams=playerid%3ASGplayer%3B&us_privacy=1YNN&aw_0_req.gdpr=true&us_privacy=1YNN");//227
            writer.newLine();
            writer.write("https://berurfm-");//228
            writer.newLine();

            // Toast.makeText(this, "initial list used", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            // Toast.makeText(getApplicationContext(), "no write", Toast.LENGTH_LONG).show();
        }
        try {
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void showConsentForm() {
        // Create a ConsentRequestParameters object.
        ConsentRequestParameters params = new ConsentRequestParameters
                .Builder()
                .build();

        consentInformation = UserMessagingPlatform.getConsentInformation(this);
        consentInformation.requestConsentInfoUpdate(
                this,
                params,
                (ConsentInformation.OnConsentInfoUpdateSuccessListener) () -> {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                            this,
                            (ConsentForm.OnConsentFormDismissedListener) loadAndShowError -> {
                                if (loadAndShowError != null) {
                                    // Consent gathering failed.

                                }

                                // Consent has been gathered.
                                if (consentInformation.canRequestAds()) {
                                    initializeMobileAdsSdk();
                                }
                            }
                    );
                },
                (ConsentInformation.OnConsentInfoUpdateFailureListener) requestConsentError -> {
                    // Consent gathering failed.
                });

        // Check if you can initialize the Google Mobile Ads SDK in parallel
        // while checking for new consent information. Consent obtained in
        // the previous session can be used to request ads.
        if (consentInformation.canRequestAds()) {
            initializeMobileAdsSdk();
        }
    }

    private void initializeMobileAdsSdk() {
        if (isMobileAdsInitializeCalled.getAndSet(true)) {
            return;
        }

        // Initialize the Google Mobile Ads SDK.
        MobileAds.initialize(this);
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
                    viewPager.setCurrentItem(0);
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


    public static void deleteCache(Context context) {
        try {
            File dir = context.getCacheDir();
            deleteDir(dir);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
            return dir.delete();
        } else if (dir != null && dir.isFile()) {
            return dir.delete();
        } else {
            return false;
        }
    }
}