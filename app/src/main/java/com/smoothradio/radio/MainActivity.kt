package com.smoothradio.radio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
import com.smoothradio.radio.feature.radio_list.util.SortDialog
import com.smoothradio.radio.service.StreamService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var radioViewModel: RadioViewModel

    private lateinit var viewPagerAdapter: ViewPagerAdapter
    lateinit var radioListRecyclerViewAdapter: RadioListRecyclerViewAdapter

    lateinit var playerManager: PlayerManager
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
        setupObservers()
        setupUI()
        setupBroadcastReceiver()
        requestPermissions()
    }

    private fun initializeComponents() {
        radioViewModel = ViewModelProvider(this).get(RadioViewModel::class.java)
        radioListRecyclerViewAdapter = RadioListRecyclerViewAdapter(
            mutableListOf(),
            RadioStationActionHandler(radioViewModel)
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

    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observer for playing station
                launch {
                    radioViewModel.playingStation.collect { station ->
                        if (station != null) {
                            Log.d("MainActivity", "setupObservers: playing station found: ${station.stationName}")
                            lastStationId = station.id
                            updateMiniPlayer(station)
                        } else {
                            Log.d("MainActivity", "setupObservers: no playing station in Room, loading from saved ID")
                            val savedStation = getStationUsingSavedId()
                            updateMiniPlayer(savedStation)
                        }
                    }
                }

                // Observer for selected station
                launch {
                    radioViewModel.selectedStation.collect {
                        hideKeyboard()
                    }
                }
            }
        }
    }
    private fun setupViewPagerAndTabs() {
        viewPagerAdapter = ViewPagerAdapter(supportFragmentManager, lifecycle)
        viewPagerAdapter.addFragments()
        binding.viewPager.adapter = viewPagerAdapter

        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("STATIONS"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("LIVE"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("DISCOVER"))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                tabPosition = tab.position
                binding.viewPager.currentItem = tabPosition
                viewPagerAdapter.notifyDataSetChanged()
                bottomSheetBehavior.state = if (tabPosition == 0) {
                    BottomSheetBehavior.STATE_COLLAPSED
                } else {
                    BottomSheetBehavior.STATE_HIDDEN
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.tabLayout.selectTab(binding.tabLayout.getTabAt(position))
            }
        })
    }

    private fun setupSearchUI() {
        binding.ivSearch.setOnClickListener {
            if (isSearchVisible) {
                binding.etSearch.visibility = View.INVISIBLE
                binding.ivClearSearch.visibility = View.INVISIBLE
                isSearchVisible = false
                hideKeyboard()
            } else {
                if (tabPosition != 0) {
                    binding.viewPager.currentItem = 0
                }
                binding.etSearch.post {
                    binding.etSearch.requestFocus()
                    inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    inputMethodManager.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
                }

                binding.etSearch.visibility = View.VISIBLE
                isSearchVisible = true
                if (binding.etSearch.text?.length ?: 0 > 0) {
                    binding.ivClearSearch.visibility = View.VISIBLE
                }
            }
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(editable: Editable) {
                radioListRecyclerViewAdapter.filter(editable.toString())
                binding.ivClearSearch.visibility = if (editable.length > 0) View.VISIBLE else View.INVISIBLE
            }
        })

        binding.ivClearSearch.setOnClickListener { binding.etSearch.setText("") }
    }

    private fun setupBroadcastReceiver() {
        val eventFilter = IntentFilter().apply {
            addAction(StreamService.ACTION_EVENT_CHANGE)
        }

        val eventReceiver = EventReceiver()

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
        CacheUtil.clearAppCache(this)
    }

    override fun onPause() {
        super.onPause()
        radioViewModel.setCurrentPage(binding.viewPager.currentItem)
//        radioViewModel.removeStreamLinkListener()
    }

    override fun onResume() {
        super.onResume()

        val currentPage = radioViewModel.currentPage.value
        binding.viewPager.currentItem = currentPage ?: 0
        binding.tabLayout.selectTab(binding.tabLayout.getTabAt(currentPage ?: 0))

        binding.ivClearSearch.visibility = if (isSearchVisible && binding.etSearch.text?.length ?: 0 > 0) {
            View.VISIBLE
        } else {
            View.INVISIBLE
        }

//        radioViewModel.getRemoteLinks()
    }

    private fun hideKeyboard() {
        inputMethodManager.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
        binding.etSearch.visibility = View.INVISIBLE
        binding.ivClearSearch.visibility = View.INVISIBLE
        isSearchVisible = false
    }

    private fun updateMiniPlayer(radioStation: RadioStation) {
        binding.miniPlayer.ivLogoMiniPlayerLayout.setImageResource(radioStation.logoResource)
        binding.miniPlayer.tvStationNameMiniPlayerLayout.text = radioStation.stationName
    }

    private fun miniPlayerPlayPause() {
        radioViewModel.setSelectedStation(getStationUsingSavedId())
    }

    fun getStationUsingSavedId(): RadioStation {
        var radioStation = RadioStation(lastStationId, 0, "", "", "", "", false, false)
        val position = radioListRecyclerViewAdapter.getPositionOfStation(lastStationId)

        if (!(radioListRecyclerViewAdapter.listIsEmpty() || position == RecyclerView.NO_POSITION)) {
            radioStation = radioListRecyclerViewAdapter.getStationAtPosition(position)
        }
        return radioStation
    }

    fun sendFirebaseAnalytics(stationName: String) {
        val event = stationName.lowercase().replace(" ", "")
        val bundle = Bundle().apply {
            putString("station_name", stationName)
        }
        firebaseAnalytics.logEvent(event, bundle)
    }

    fun getAdapter(): RadioListRecyclerViewAdapter = radioListRecyclerViewAdapter

    private inner class EventReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getStringExtra(StreamService.EXTRA_STATE)

            when (state) {
                StreamService.StreamStates.BUFFERING, StreamService.StreamStates.PREPARING -> showBufferingState()
                StreamService.StreamStates.PLAYING -> showPlayingState()
                else -> showStoppedState()
            }
            // update mini player
            binding.miniPlayer.tvStatusMiniPlayerLayout.text = state
        }

        private fun showBufferingState() {
            binding.miniPlayer.ivPlayMiniPlayerLayout.visibility = View.INVISIBLE
            binding.miniPlayer.loadingAnimationMiniPlayerLayout.visibility = View.VISIBLE
        }

        private fun showPlayingState() {
            binding.miniPlayer.ivPlayMiniPlayerLayout.setImageResource(R.drawable.pauseicon)
            binding.miniPlayer.ivPlayMiniPlayerLayout.visibility = View.VISIBLE
            binding.miniPlayer.loadingAnimationMiniPlayerLayout.visibility = View.INVISIBLE
        }

        private fun showStoppedState() {
            binding.miniPlayer.ivPlayMiniPlayerLayout.visibility = View.VISIBLE
            binding.miniPlayer.ivPlayMiniPlayerLayout.setImageResource(R.drawable.playicon)
            binding.miniPlayer.loadingAnimationMiniPlayerLayout.visibility = View.INVISIBLE
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
                startActivity(Intent(this, AboutFragment::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    inner class RadioStationActionHandler(private val radioViewModel: RadioViewModel) :
        RadioListRecyclerViewAdapter.RadioStationActionListener {
        override fun onStationSelected(station: RadioStation) {
            radioViewModel.setSelectedStation(station)
        }

        override fun onToggleFavorite(station: RadioStation, isFavorite: Boolean) {
            radioViewModel.updateFavoriteStatus(station.id, isFavorite)
        }

        override fun onRequestHideKeyboard() {
            hideKeyboard()
        }

        override fun onRequestshowToast(message: String) {
            showToast(message)
        }

        private fun showToast(message: String) {
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
    }
}