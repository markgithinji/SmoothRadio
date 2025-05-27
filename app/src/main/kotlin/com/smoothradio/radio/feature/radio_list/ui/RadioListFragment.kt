package com.smoothradio.radio.feature.radio_list.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.smoothradio.radio.MainActivity
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.ui.RadioViewModel
import com.smoothradio.radio.core.ui.dialog.SortOption
import com.smoothradio.radio.core.util.PlayerManager
import com.smoothradio.radio.databinding.FragmentMusicListBinding
import com.smoothradio.radio.feature.radio_list.ui.adapter.RadioListRecyclerViewAdapter
import com.smoothradio.radio.feature.radio_list.util.RadioStationActionHandler
import com.smoothradio.radio.service.StreamService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RadioListFragment : Fragment() {

    @Inject
    lateinit var playerManager: PlayerManager

    private var _binding: FragmentMusicListBinding? = null
    private val binding get() = _binding!!

    private lateinit var radioViewModel: RadioViewModel
    private lateinit var radioListRecyclerViewAdapter: RadioListRecyclerViewAdapter

    private val eventReceiver = EventReceiver()
    private lateinit var eventIntent: Intent

    private var currentStation: RadioStation? = null

    private var mainActivity: MainActivity? = null
    private lateinit var fragmentActivity: FragmentActivity


    override fun onAttach(context: Context) {
        super.onAttach(context)
        fragmentActivity = context as FragmentActivity
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainActivity = requireActivity() as? MainActivity

        initializeComponents()
        setupBroadcastReceiver()
    }

    private fun initializeComponents() {
        radioViewModel = ViewModelProvider(requireActivity())[RadioViewModel::class.java]
        eventIntent =
            Intent(StreamService.ACTION_EVENT_CHANGE).setPackage(requireActivity().packageName)
    }

    private fun setupBroadcastReceiver() {
        val eventFilter = IntentFilter(StreamService.ACTION_EVENT_CHANGE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            fragmentActivity.registerReceiver(
                eventReceiver,
                eventFilter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            fragmentActivity.registerReceiver(eventReceiver, eventFilter)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMusicListBinding.inflate(inflater, container, false)
        radioListRecyclerViewAdapter = RadioListRecyclerViewAdapter(
            mutableListOf(),
            RadioStationHandler(radioViewModel)
        )
        radioViewModel.observeAndProcessRemoteLinks()
        collectFlows()
        setupRecyclerView()
        return binding.root
    }

    private fun collectFlows() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // All stations observer
                launch {
                    radioViewModel.allStations.collect { stations ->
                        radioListRecyclerViewAdapter.update(stations)
                    }
                }
                // Selected station observer
                launch {
                    radioViewModel.selectedStation.collect { station ->
                        station?.let {
                            currentStation = it
                            playerManager.setRadioStation(it)

                            if (it.isPlaying) {
                                playerManager.playOrStop()
                            } else {
                                playerManager.playFromMainActivity()
                            }
                            radioViewModel.savePlayingStationId(it.id)
                        }
                    }
                }
                // Favorite stations observer
                launch {
                    radioViewModel.favoriteStations.collect { favorites ->
                        radioListRecyclerViewAdapter.updateFavorites(favorites)
                    }
                }
            }
        }
    }

    private fun setupRecyclerView() {
        binding.rvRadioList.apply {
            adapter = radioListRecyclerViewAdapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            setHasFixedSize(true)
        }
        setupRecyclerViewScrollBehavior()
    }

    private fun setupRecyclerViewScrollBehavior() {
        binding.rvRadioList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                mainActivity?.bottomSheetBehavior?.let { bottomSheetBehavior ->
                    if (!recyclerView.canScrollVertically(1)) {
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                    } else {
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                    }
                }

            }
        })
    }

    override fun onResume() {
        super.onResume()
        mainActivity = requireActivity() as? MainActivity

        playerManager.bindActivity(requireActivity())

        currentStation?.let {
            radioListRecyclerViewAdapter.setPlayingStation(it.id)
        }

        // update ui or get currently playing state from service
        if (playerManager.isShowingAd) {
            broadcastState(StreamService.StreamStates.PREPARING)
        } else {
            val intent = Intent(StreamService.ACTION_GET_STATE).apply {
                setPackage(fragmentActivity.packageName)
            }
            fragmentActivity.sendBroadcast(intent)
        }
    }

    private fun broadcastState(state: String) {
        eventIntent.putExtra(StreamService.EXTRA_STATE, state)
        requireActivity().sendBroadcast(eventIntent)
    }

    override fun onPause() {
        super.onPause()
        playerManager.unbindActivity()
    }

    override fun onDestroy() {
        super.onDestroy()
        requireActivity().unregisterReceiver(eventReceiver)
        playerManager.unregisterBroadcastReceiver()
        _binding = null
    }

    fun filterStations(query: String) {
        radioListRecyclerViewAdapter.filter(query)
    }

    fun sortStations(option: SortOption) {
        when (option) {
            SortOption.POPULAR -> radioListRecyclerViewAdapter.sortPopular()
            SortOption.ASCENDING -> radioListRecyclerViewAdapter.sortAndDisplay(
                RadioListRecyclerViewAdapter.DisplayState.ASCENDING
            )

            SortOption.DESCENDING -> radioListRecyclerViewAdapter.sortAndDisplay(
                RadioListRecyclerViewAdapter.DisplayState.DESCENDING
            )

            SortOption.FAVORITES -> radioListRecyclerViewAdapter.sortFavourites()
        }
    }

    private inner class EventReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val state = intent?.getStringExtra(StreamService.EXTRA_STATE) ?: ""
            radioListRecyclerViewAdapter.setState(state)
            currentStation?.let { radioListRecyclerViewAdapter.updateStation(it) }
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

        override fun onRequestShowToast(message: String) {
            showToast(message)
        }

        private fun showToast(message: String) {
            Toast.makeText(fragmentActivity, message, Toast.LENGTH_SHORT).show()
        }
    }
}
