package com.smoothradio.radio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdRequest.ERROR_CODE_INVALID_REQUEST
import com.google.android.gms.ads.AdRequest.ERROR_CODE_NO_FILL
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.tabs.TabLayout
import com.google.firebase.analytics.FirebaseAnalytics
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.ui.PlayCommand
import com.smoothradio.radio.core.ui.PlayerControlViewModel
import com.smoothradio.radio.core.ui.RadioViewModel
import com.smoothradio.radio.core.ui.adapter.ViewPagerAdapter
import com.smoothradio.radio.core.ui.dialog.SortDialog
import com.smoothradio.radio.core.ui.dialog.SortOption
import com.smoothradio.radio.core.ui.dialog.SortOptionListener
import com.smoothradio.radio.core.util.AdConfig
import com.smoothradio.radio.core.util.CacheUtil
import com.smoothradio.radio.core.util.ConsentHelper
import com.smoothradio.radio.databinding.ActivityMainBinding
import com.smoothradio.radio.feature.about.ui.AboutFragment
import com.smoothradio.radio.service.StreamService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val eventReceiver: BroadcastReceiver = EventReceiver()

    private lateinit var binding: ActivityMainBinding

    private lateinit var viewPagerAdapter: ViewPagerAdapter

    private lateinit var inputMethodManager: InputMethodManager
    lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var firebaseAnalytics: FirebaseAnalytics

    private val playerControlViewModel: PlayerControlViewModel by viewModels()
    private val radioViewModel: RadioViewModel by viewModels()

    private lateinit var serviceIntent: Intent

    private var interstitialAd: InterstitialAd? = null
    private var isShowingAd = false
    private var adFailedCountdown = 0

    private var isPlaying = false
    private var currentStation: RadioStation? = null

    private var tabPosition = 0
    private var isSearchVisible = false
    private var isRestoringSearch = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        installSplashScreen()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyEdgeToEdgeForVersion()
        initializeComponents()
        collectFlows()
        setupUI()
        setupBroadcastReceiver()
        requestPermissions()
    }

    private fun applyEdgeToEdgeForVersion() {
        val isAndroid15OrAbove = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

        if (isAndroid15OrAbove) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            // Handle insets manually
            ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    systemBars.bottom
                )
                insets
            }

            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.WHITE

            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars =
                true
        }
    }

    private fun initializeComponents() {
        inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        bottomSheetBehavior = BottomSheetBehavior.from(binding.miniPlayer.bottomSheetLayout)
        serviceIntent = Intent(this, StreamService::class.java)

        ConsentHelper(this).showConsentForm()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        setupViewPagerAndTabs()
        setupSearchUI()
        binding.miniPlayer.ivPlayMiniPlayerLayout.setOnClickListener { miniPlayerPlayPause() }
    }

    private fun miniPlayerPlayPause() {
        lifecycleScope.launch {
            val station = playerControlViewModel.playingStation.value
            station?.let {
                playerControlViewModel.requestPlayStation(it)
            }
        }
    }

    private fun collectFlows() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    playerControlViewModel.playingStation.collect { station ->
                        station?.let { currentStation ->
                            updateMiniPlayer(currentStation)
                            hideKeyboard()
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                playerControlViewModel.playCommand.collect { command ->
                    when (command) {
                        is PlayCommand.PlayStation -> {
                            currentStation = command.station
                            if (command.station.isPlaying) {
                                playOrStop()
                            } else {
                                startNewPlay()
                            }
                            playerControlViewModel.savePlayingStationId(command.station.id)
                        }

                        is PlayCommand.Refresh -> {
                            refresh()
                        }
                    }
                }
            }
        }
    }

    private fun updateMiniPlayer(radioStation: RadioStation) = with(binding.miniPlayer) {
        ivLogoMiniPlayerLayout.setImageResource(radioStation.logoResource)
        tvStationNameMiniPlayerLayout.text = radioStation.stationName
    }

    private fun sendFirebaseAnalytics(stationName: String) {
        val event = stationName.lowercase().replace(" ", "")
        val bundle = Bundle().apply {
            putString("station_name", stationName)
        }
        firebaseAnalytics.logEvent(event, bundle)
    }

    private fun setupViewPagerAndTabs() {
        viewPagerAdapter = ViewPagerAdapter(
            fragmentManager = supportFragmentManager,
            lifecycle = lifecycle,
            viewPager2 = binding.viewPager,
            swipeSensitivityFactor = 4
        )
        binding.viewPager.adapter = viewPagerAdapter

        // Define tab titles
        val tabTitles = listOf(
            getString(R.string.tab_stations),
            getString(R.string.tab_live),
            getString(R.string.tab_discover)
        )

        tabTitles.forEach { title ->
            binding.tabLayout.addTab(binding.tabLayout.newTab().setText(title))
        }

        // Setup tab selection listener
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                tabPosition = tab.position
                binding.viewPager.currentItem = tabPosition
                viewPagerAdapter.notifyDataSetChanged()

                // Update bottom sheet state based on tab selection
                bottomSheetBehavior.state = if (tabPosition == 0) {
                    BottomSheetBehavior.STATE_COLLAPSED
                } else {
                    BottomSheetBehavior.STATE_HIDDEN
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        // Sync the ViewPager with the TabLayout
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.tabLayout.selectTab(binding.tabLayout.getTabAt(position))
            }
        })
    }

    private fun setupSearchUI() = with(binding) {
        ivSearch.setOnClickListener {
            if (isSearchVisible) {
                etSearch.isVisible = false
                ivClearSearch.isVisible = false
                isSearchVisible = false
                hideKeyboard()
            } else {
                if (tabPosition != 0) viewPager.currentItem = 0

                etSearch.post {
                    etSearch.requestFocus()
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                        .showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT)
                }

                etSearch.isVisible = true
                isSearchVisible = true
                ivClearSearch.isVisible = etSearch.text?.isNotEmpty() == true
            }
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(editable: Editable) {
                if (isRestoringSearch) return
                viewPagerAdapter.getRadioListFragment()?.filterStations(editable.toString())
                ivClearSearch.isVisible = editable.isNotEmpty()
            }
        })
        // Delay the first change check until restoration is complete. Prevents crash on configuration changes
        etSearch.post {
            isRestoringSearch = false
        }

        ivClearSearch.setOnClickListener { etSearch.setText("") }
    }

    private fun hideKeyboard() {
        inputMethodManager.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
        binding.apply {
            etSearch.visibility = View.INVISIBLE
            ivClearSearch.visibility = View.INVISIBLE
        }
        isSearchVisible = false
    }

    private fun setupBroadcastReceiver() {
        val eventFilter = IntentFilter(StreamService.ACTION_EVENT_CHANGE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(eventReceiver, eventFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(eventReceiver, eventFilter)
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissions = arrayOf(android.Manifest.permission.POST_NOTIFICATIONS)
            ActivityCompat.requestPermissions(this, permissions, 0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(eventReceiver)
        CacheUtil.clearAppCache(this)
    }

    override fun onPause() {
        super.onPause()
        radioViewModel.setCurrentPage(binding.viewPager.currentItem)
    }

    override fun onResume() {
        super.onResume()

        val currentPage = radioViewModel.currentPage.value
        binding.viewPager.currentItem = currentPage
        binding.tabLayout.selectTab(binding.tabLayout.getTabAt(currentPage))

        binding.ivClearSearch.visibility =
            if (isSearchVisible && (binding.etSearch.text?.length ?: 0) > 0) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sort -> {
                if (tabPosition != 0) {
                    binding.viewPager.currentItem = 0
                }
                SortDialog(SortOptionHandler()).show(supportFragmentManager, "sortDialog")
                true
            }

            R.id.action_info -> {
                AboutFragment().show(supportFragmentManager, "aboutDialog")
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    fun startNewPlay() {
        LoggingHelper.playback(
            "MAIN - Play from main activity | Playing: $isPlaying, ShowingAd: $isShowingAd",
            currentStation?.id
        )

        isPlaying = true
        if (isShowingAd) {
            LoggingHelper.w("MAIN - Blocked by isShowingAd")
            return
        }

        serviceIntent.action = StreamService.ACTION_SHOW_AD
        startStreamService()
        loadInterstitialAd()
        checkInternet()
        sendFirebaseAnalytics(currentStation?.stationName ?: "Unknown station")
    }

    fun playOrStop() {
        if (isPlaying) {
            stopService(serviceIntent)
            isPlaying = false
            return
        }

        if (isShowingAd) {
            LoggingHelper.w("Blocked by isShowingAd")
            return
        }

        isPlaying = true

        serviceIntent.action = StreamService.ACTION_SHOW_AD
        startStreamService()
        loadInterstitialAd()
        checkInternet()
    }

    private fun refresh() {
        if (isShowingAd) return

        isPlaying = true
        serviceIntent.action = StreamService.ACTION_SHOW_AD
        startStreamService()
        loadInterstitialAd()
        checkInternet()
        showToast(getString(R.string.refreshed))
    }

    private fun startStreamService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun playOnly() {
        serviceIntent.action = StreamService.ACTION_START
        serviceIntent.putExtra(StreamService.EXTRA_LINK, currentStation?.streamLink)
        serviceIntent.putExtra(StreamService.EXTRA_LOGO, currentStation?.logoResource)
        serviceIntent.putExtra(StreamService.EXTRA_STATION_NAME, currentStation?.stationName)
        startStreamService()
        playerControlViewModel.updatePlaybackState(StreamService.StreamStates.PREPARING)
        isPlaying = true
    }

    private fun loadInterstitialAd() {
        LoggingHelper.playback(
            "LOAD_AD - Starting ad load | isShowingAd: $isShowingAd",
            currentStation?.id
        )
        isShowingAd = true

        if (interstitialAd != null) {
            LoggingHelper.playback(
                "LOAD_AD - Ad already loaded, showing immediately",
                currentStation?.id
            )
            if (isPlaying) showAd()
            return
        }

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            this,
            AdConfig.interstitialAdId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    LoggingHelper.playback("LOAD_AD - Ad loaded successfully", currentStation?.id)
                    interstitialAd = ad
                    if (isPlaying) showAd()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    LoggingHelper.e("LOAD_AD - Ad failed to load: ${loadAdError.code}")
                    interstitialAd = null
                    handleAdLoadFailure(loadAdError)
                }
            }
        )
    }

    private fun showAd() {
        val ad = interstitialAd ?: run {
            loadInterstitialAd()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                playOnly()
                isShowingAd = false
                preloadInterstitialAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                interstitialAd = null
                isShowingAd = false

                AnalyticsHelper.trackPlaybackEvent(
                    "ad_show_failed_code_${adError.code}", currentStation?.id, mapOf(
                        "ad_error_type" to "show_failed"
                    )
                )
                stopService(serviceIntent)
            }
        }

        ad.show(this)
    }

    private fun handleAdLoadFailure(loadAdError: LoadAdError) {
        AnalyticsHelper.trackPlaybackEvent(
            "ad_load_failed_code_${loadAdError.code}", currentStation?.id, mapOf(
                "ad_error_type" to when (loadAdError.code) {
                    ERROR_CODE_INVALID_REQUEST -> "invalid_request"
                    ERROR_CODE_NO_FILL -> "no_fill"
                    else -> "unknown"
                },
                "attempt_count" to adFailedCountdown + 1
            )
        )

        adFailedCountdown++
        if (adFailedCountdown < MAX_AD_LOAD_ATTEMPTS) {
            loadInterstitialAd()
        } else {
            adFailedCountdown = 0
            isShowingAd = false
            playOnly()
        }
    }

    private fun preloadInterstitialAd() {
        if (interstitialAd == null) {
            val adRequest = AdRequest.Builder().build()
            InterstitialAd.load(
                this,
                AdConfig.interstitialAdId,
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        interstitialAd = null
                        preloadInterstitialAd()
                    }
                }
            )
        }
    }

    private fun checkInternet() {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return

        val network = cm.activeNetwork
        val capabilities = cm.getNetworkCapabilities(network)
        val connected =
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        if (!connected) {
            showToast(getString(R.string.check_internet))
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    inner class SortOptionHandler : SortOptionListener {
        override fun onSortOptionSelected(option: SortOption) {
            viewPagerAdapter.getRadioListFragment()?.sortStations(option)
        }
    }

    private inner class EventReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getStringExtra(StreamService.EXTRA_STATE) ?: ""

            isPlaying = when (state) {
                StreamService.StreamStates.PREPARING,
                StreamService.StreamStates.PLAYING,
                StreamService.StreamStates.BUFFERING -> true

                StreamService.StreamStates.IDLE,
                StreamService.StreamStates.ENDED -> false

                else -> false
            }

            playerControlViewModel.updatePlaybackState(state)

            when (state) {
                StreamService.StreamStates.BUFFERING,
                StreamService.StreamStates.PREPARING -> {
                    showBufferingState(state)
                }

                StreamService.StreamStates.PLAYING -> {
                    showPlayingState(state)
                }

                else -> {
                    showStoppedState()
                }
            }
        }

        private fun showBufferingState(state: String) = with(binding.miniPlayer) {
            tvStatusMiniPlayerLayout.text = state
            ivPlayMiniPlayerLayout.isVisible = false
            loadingAnimationMiniPlayerLayout.isVisible = true
        }

        private fun showPlayingState(state: String) = with(binding.miniPlayer) {
            tvStatusMiniPlayerLayout.text = state
            ivPlayMiniPlayerLayout.apply {
                setImageResource(R.drawable.pauseicon)
                isVisible = true
            }
            loadingAnimationMiniPlayerLayout.isVisible = false
        }

        private fun showStoppedState() = with(binding.miniPlayer) {
            tvStatusMiniPlayerLayout.text = ""
            ivPlayMiniPlayerLayout.apply {
                setImageResource(R.drawable.playicon)
                isVisible = true
            }
            loadingAnimationMiniPlayerLayout.isVisible = false
        }
    }

    companion object {
        private const val MAX_AD_LOAD_ATTEMPTS = 2
    }
}
