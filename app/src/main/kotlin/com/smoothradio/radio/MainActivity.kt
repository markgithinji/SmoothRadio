package com.smoothradio.radio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.tabs.TabLayout
import com.google.firebase.analytics.FirebaseAnalytics
import com.smoothradio.radio.core.model.RadioStation
import com.smoothradio.radio.core.ui.RadioViewModel
import com.smoothradio.radio.core.ui.adapter.ViewPagerAdapter
import com.smoothradio.radio.core.util.CacheUtil
import com.smoothradio.radio.core.util.ConsentHelper
import com.smoothradio.radio.core.util.PlayerManager
import com.smoothradio.radio.databinding.ActivityMainBinding
import com.smoothradio.radio.feature.about.ui.AboutFragment
import com.smoothradio.radio.feature.radio_list.ui.adapter.RadioListRecyclerViewAdapter
import com.smoothradio.radio.feature.radio_list.ui.adapter.RadioStationActionHandler
import com.smoothradio.radio.feature.radio_list.util.SortDialog
import com.smoothradio.radio.service.StreamService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val eventReceiver: BroadcastReceiver = EventReceiver()
    lateinit var playerManager: PlayerManager
    lateinit var radioListRecyclerViewAdapter: RadioListRecyclerViewAdapter

    private lateinit var binding: ActivityMainBinding
    private lateinit var radioViewModel: RadioViewModel

    private lateinit var viewPagerAdapter: ViewPagerAdapter

    private lateinit var inputMethodManager: InputMethodManager
    lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var firebaseAnalytics: FirebaseAnalytics

    private var tabPosition = 0
    private var lastStationId = 0
    private var isSearchVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        installSplashScreen()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeComponents()
        collectFlows()
        setupUI()
        setupBroadcastReceiver()
        requestPermissions()
    }

    private fun initializeComponents() {
        radioViewModel = ViewModelProvider(this)[RadioViewModel::class.java]
        radioListRecyclerViewAdapter = RadioListRecyclerViewAdapter(
            mutableListOf(),
            RadioStationHandler(radioViewModel)
        )
        playerManager = PlayerManager(this)
        inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        bottomSheetBehavior = BottomSheetBehavior.from(binding.miniPlayer.bottomSheetLayout)

        ConsentHelper(this).showConsentForm()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        setupViewPagerAndTabs()
        setupSearchUI()
        binding.miniPlayer.ivPlayMiniPlayerLayout.setOnClickListener { miniPlayerPlayPause() }
    }

    private fun collectFlows() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe for playing station
                launch {
                    radioViewModel.playingStation.collect { station ->
                        val currentStation = station ?: getStationUsingSavedId()
                        lastStationId = currentStation.id
                        updateMiniPlayer(currentStation)
                        sendFirebaseAnalytics(currentStation.stationName)
                        hideKeyboard()
                    }
                }
            }
        }
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
                    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
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
                radioListRecyclerViewAdapter.filter(editable.toString())
                ivClearSearch.isVisible = editable.isNotEmpty()
            }
        })

        ivClearSearch.setOnClickListener { etSearch.setText("") }
    }


    private fun setupBroadcastReceiver() {
        val eventFilter = IntentFilter(StreamService.ACTION_EVENT_CHANGE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(eventReceiver, eventFilter, Context.RECEIVER_NOT_EXPORTED)
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
        CacheUtil.clearAppCache(this)
        unregisterReceiver(eventReceiver)
        playerManager.unregisterBroadcastReceiver()
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

    private fun hideKeyboard() {
        inputMethodManager.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
        binding.apply {
            etSearch.visibility = View.INVISIBLE
            ivClearSearch.visibility = View.INVISIBLE
        }
        isSearchVisible = false
    }

    private fun updateMiniPlayer(radioStation: RadioStation) = with(binding.miniPlayer) {
        ivLogoMiniPlayerLayout.setImageResource(radioStation.logoResource)
        tvStationNameMiniPlayerLayout.text = radioStation.stationName
    }

    private fun miniPlayerPlayPause() {
        radioViewModel.setSelectedStation(getStationUsingSavedId())
    }

    fun getStationUsingSavedId(): RadioStation {
        var radioStation = RadioStation(
            id = lastStationId,
            logoResource = 0,
            stationName = "",
            frequency = "",
            location = "",
            streamLink = "",
            isPlaying = true,
            isFavorite = false
        )
        val position = radioListRecyclerViewAdapter.getPositionOfStation(lastStationId)

        if (!(radioListRecyclerViewAdapter.listIsEmpty() || position == RecyclerView.NO_POSITION)) {
            radioStation = radioListRecyclerViewAdapter.getStationAtPosition(position)
        }
        return radioStation
    }

    private fun sendFirebaseAnalytics(stationName: String) {
        val event = stationName.lowercase().replace(" ", "")
        val bundle = Bundle().apply {
            putString("station_name", stationName)
        }
        firebaseAnalytics.logEvent(event, bundle)
    }

    private inner class EventReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getStringExtra(StreamService.EXTRA_STATE)
            binding.miniPlayer.tvStatusMiniPlayerLayout.text = state

            when (state) {
                StreamService.StreamStates.BUFFERING,
                StreamService.StreamStates.PREPARING -> showBufferingState()

                StreamService.StreamStates.PLAYING -> showPlayingState()
                else -> showStoppedState()
            }
        }

        private fun showBufferingState() = with(binding.miniPlayer) {
            ivPlayMiniPlayerLayout.isVisible = false
            loadingAnimationMiniPlayerLayout.isVisible = true
        }

        private fun showPlayingState() = with(binding.miniPlayer) {
            ivPlayMiniPlayerLayout.apply {
                setImageResource(R.drawable.pauseicon)
                isVisible = true
            }
            loadingAnimationMiniPlayerLayout.isVisible = false
        }

        private fun showStoppedState() = with(binding.miniPlayer) {
            ivPlayMiniPlayerLayout.apply {
                setImageResource(R.drawable.playicon)
                isVisible = true
            }
            loadingAnimationMiniPlayerLayout.isVisible = false
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
                SortDialog().show(supportFragmentManager, "dialogueFragment")
                true
            }

            R.id.action_info -> {
                AboutFragment().show(supportFragmentManager, "aboutDialog")
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }


    private inner class RadioStationHandler(
        private val radioViewModel: RadioViewModel
    ) : RadioStationActionHandler {

        override fun onStationSelected(station: RadioStation) {
            radioViewModel.setSelectedStation(station)
        }

        override fun onToggleFavorite(station: RadioStation, isFavorite: Boolean) {
            radioViewModel.updateFavoriteStatus(station.id, isFavorite)
        }

        override fun onRequestHideKeyboard() {
            hideKeyboard()
        }

        override fun onRequestShowToast(message: String) {
            showToast(message)
        }

        private fun showToast(message: String) {
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

}