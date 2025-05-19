package com.smoothradio.radio.feature.radio_list.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.smoothradio.radio.core.model.RadioStation
import com.smoothradio.radio.core.ui.RadioViewModel
import com.smoothradio.radio.core.util.PlayerManager
import com.smoothradio.radio.core.util.Resource
import com.smoothradio.radio.databinding.FragmentMusicListBinding
import com.smoothradio.radio.feature.radio_list.ui.adapter.RadioListRecyclerViewAdapter
import com.smoothradio.radio.feature.radio_list.util.RadioStationsHelper
import com.smoothradio.radio.service.StreamService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RadioListFragment : Fragment() {

    private var _binding: FragmentMusicListBinding? = null
    private val binding get() = _binding!!

    private lateinit var radioViewModel: RadioViewModel
    private lateinit var radioListRecyclerViewAdapter: RadioListRecyclerViewAdapter
    private val playerManager: PlayerManager?
        get() = mainActivity?.playerManager

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
        radioListRecyclerViewAdapter = mainActivity?.radioListRecyclerViewAdapter!!

        collectFlows()
        setupRecyclerView()
        return binding.root
    }

    private fun collectFlows() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                // Remote links observer
                launch {
                    radioViewModel.remoteLinks.collect { resource ->
                        when (resource) {
                            is Resource.Success -> {
                                val newLinks = resource.data
                                val localStations = radioViewModel.allStations.first()
                                val newStations =
                                    RadioStationsHelper.createRadioStations(newLinks, localStations)
                                radioViewModel.insertStations(newStations)
                                Log.d(
                                    "RadioListFragment",
                                    "Remote links collected: ${newStations.size}"
                                )
                            }

                            is Resource.Error -> {
                                Log.e(
                                    "RadioListFragment",
                                    "Failed to load remote links: ${resource.message}"
                                )
                            }

                            Resource.Loading -> {
                                // Optionally show loading state
                            }
                        }
                    }

                }
                // All stations observer
                launch {
                    radioViewModel.allStations.collect { stations ->
                        Log.d("RadioListFragment", "All stations collected" + stations.size)
                        radioListRecyclerViewAdapter.update(stations)
                    }
                }
                // Selected station observer
                launch {
                    radioViewModel.selectedStation.collect { station ->
                        station?.let {
                            Log.d(
                                "RadioListFragment",
                                "Selected station collected" + station.isPlaying
                            )
                            currentStation = it
                            playerManager?.setRadioStation(it)

                            if (it.isPlaying) {
                                playerManager?.playOrStop()
                            } else {
                                playerManager?.playFromMainActivity()
                            }
                            radioViewModel.savePlayingStationId(it.id)
                        }
                    }
                }
                // Favorite stations observer
                launch {
                    radioViewModel.favoriteStations.collect { favorites ->
                        Log.d("RadioListFragment", "Favorite stations collected")
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
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
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

    inner class EventReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val state = intent?.getStringExtra(StreamService.EXTRA_STATE) ?: ""
            radioListRecyclerViewAdapter.setState(state)
            currentStation?.let { radioListRecyclerViewAdapter.updateStation(it) }
        }
    }

    override fun onResume() {
        super.onResume()
        mainActivity = requireActivity() as MainActivity

        val station = currentStation ?: mainActivity?.getStationUsingSavedId()
        station?.let {
            currentStation = it
            radioListRecyclerViewAdapter.setPlayingStation(it.id)
        }

        if (playerManager?.isShowingAd == true) {
            broadcastState(StreamService.StreamStates.PREPARING)
        } else {
            val intent = Intent(StreamService.ACTION_GET_STATE).apply {
                setPackage(requireContext().packageName)
            }
            requireContext().sendBroadcast(intent)
        }
    }


    private fun broadcastState(state: String) {
        eventIntent.putExtra(StreamService.EXTRA_STATE, state)
        requireActivity().sendBroadcast(eventIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        requireActivity().unregisterReceiver(eventReceiver)
        _binding = null
    }
}
