package com.smoothradio.radio.feature.discover.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.smoothradio.radio.MainActivity
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.ui.RadioViewModel
import com.smoothradio.radio.core.util.PlayerManager
import com.smoothradio.radio.databinding.FragmentDiscoverBinding
import com.smoothradio.radio.feature.discover.ui.adapter.DiscoverRecyclerViewAdapter
import com.smoothradio.radio.feature.discover.util.CategoryHelper
import com.smoothradio.radio.service.StreamService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DiscoverFragment : Fragment() {

    @Inject
    lateinit var playerManager: PlayerManager

    private var _binding: FragmentDiscoverBinding? = null
    private val binding get() = _binding!!

    private lateinit var discoverRecyclerViewAdapter: DiscoverRecyclerViewAdapter
    private lateinit var radioViewModel: RadioViewModel

    private lateinit var fragmentActivity: FragmentActivity
    private var mainActivity: MainActivity? = null

    private var eventReceiver: EventReceiver = EventReceiver()
    private lateinit var eventIntent: Intent
    private var currentStation: RadioStation? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        fragmentActivity = context as FragmentActivity
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainActivity = requireActivity() as? MainActivity

        eventIntent =
            Intent(StreamService.ACTION_EVENT_CHANGE).setPackage(fragmentActivity.packageName)
        setupBroadcastReceiver()
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View {
        _binding = FragmentDiscoverBinding.inflate(inflater, container, false)
        radioViewModel = ViewModelProvider(fragmentActivity)[RadioViewModel::class.java]
        discoverRecyclerViewAdapter =
            DiscoverRecyclerViewAdapter(emptyList(), RadioStationActionHandler(radioViewModel))
        collectFlows()
        setupRecyclerView()

        return binding.root
    }

    private fun collectFlows() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    radioViewModel.allStations.collect { stations ->
                        val categoryList = CategoryHelper.createCategories(stations)
                        discoverRecyclerViewAdapter.updateCategoryList(categoryList)
                    }
                }
                launch {
                    radioViewModel.selectedStation.collect { radioStation ->
                        currentStation = radioStation
                    }
                }
                launch {
                    radioViewModel.favoriteStations.collect { favorites ->
                        discoverRecyclerViewAdapter.updateFavorites(favorites)
                    }
                }
                launch {
                    radioViewModel.playingStation.collect { station ->
                        currentStation = station
                    }
                }
            }
        }
    }

    private fun setupRecyclerView() {
        binding.rvDiscover.apply {
            adapter = discoverRecyclerViewAdapter
            layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
            setHasFixedSize(true)
        }
    }

    private fun setupBroadcastReceiver() {
        val eventFilter = IntentFilter().apply {
            addAction(StreamService.ACTION_EVENT_CHANGE)
        }

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

    override fun onResume() {
        super.onResume()
        playerManager.bindActivity(fragmentActivity)

        if (playerManager.isShowingAd) {
            broadcastState(StreamService.StreamStates.PREPARING)
        } else {
            val getStateFromServiceIntent =
                Intent(StreamService.ACTION_GET_STATE).setPackage(fragmentActivity.packageName)
            fragmentActivity.sendBroadcast(getStateFromServiceIntent)
        }
    }

    override fun onPause() {
        super.onPause()
        playerManager.unbindActivity()
    }

    private fun broadcastState(state: String) {
        eventIntent.putExtra(StreamService.EXTRA_STATE, state)
        fragmentActivity.sendBroadcast(eventIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        fragmentActivity.unregisterReceiver(eventReceiver)
        _binding = null
    }

    /**
     * Inner class that listens for events from the StreamService.
     * This receiver is responsible for updating the nested recyclerviews based on the state of the audio stream.
     */
    inner class EventReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val state = intent?.getStringExtra(StreamService.EXTRA_STATE)
            discoverRecyclerViewAdapter.setSelectedStationWithState(
                currentStation ?: return,
                state ?: ""
            )
        }
    }

    inner class RadioStationActionHandler(private val radioViewModel: RadioViewModel) :
        com.smoothradio.radio.feature.discover.util.RadioStationActionHandler {
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
