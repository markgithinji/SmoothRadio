package com.smoothradio.radio.feature.discover.ui

import android.content.Context
import android.content.Intent
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
import com.smoothradio.radio.core.ui.PlayerControlViewModel
import com.smoothradio.radio.core.ui.RadioViewModel
import com.smoothradio.radio.databinding.FragmentDiscoverBinding
import com.smoothradio.radio.feature.discover.ui.adapter.DiscoverRecyclerViewAdapter
import com.smoothradio.radio.feature.discover.util.CategoryHelper
import com.smoothradio.radio.service.StreamService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DiscoverFragment : Fragment() {
    private var _binding: FragmentDiscoverBinding? = null
    private val binding get() = _binding!!

    private lateinit var discoverRecyclerViewAdapter: DiscoverRecyclerViewAdapter
    private lateinit var radioViewModel: RadioViewModel
    private lateinit var playerControlViewModel: PlayerControlViewModel

    private lateinit var fragmentActivity: FragmentActivity
    private var mainActivity: MainActivity? = null

    private lateinit var eventIntent: Intent

    override fun onAttach(context: Context) {
        super.onAttach(context)
        fragmentActivity = context as FragmentActivity
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainActivity = requireActivity() as? MainActivity

        eventIntent =
            Intent(StreamService.ACTION_EVENT_CHANGE).setPackage(fragmentActivity.packageName)
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View {
        _binding = FragmentDiscoverBinding.inflate(inflater, container, false)
        playerControlViewModel =
            ViewModelProvider(fragmentActivity)[PlayerControlViewModel::class.java]
        radioViewModel = ViewModelProvider(fragmentActivity)[RadioViewModel::class.java]
        discoverRecyclerViewAdapter =
            DiscoverRecyclerViewAdapter(emptyList(), RadioStationActionHandler(radioViewModel))
        collectFlows()
        setupRecyclerView()

        return binding.root
    }

    private fun collectFlows() {
        lifecycleScope.launch {
            launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    radioViewModel.allStations.collect { stations ->
                        val categoryList = CategoryHelper.createCategories(stations)
                        discoverRecyclerViewAdapter.updateCategoryList(categoryList)
                    }
                }
            }

            launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    radioViewModel.favoriteStations.collect { favorites ->
                        discoverRecyclerViewAdapter.updateFavorites(favorites)
                    }
                }
            }
            launch {
                repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    combine(
                        playerControlViewModel.playingStation,
                        playerControlViewModel.playbackState
                    ) { station, state ->
                        station to state
                    }.collect { (station, state) ->
                        if (station != null) {
                            discoverRecyclerViewAdapter.setSelectedStationWithState(station, state)
                        }
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


    override fun onResume() {
        super.onResume()

        // send broadcast to ask for ui updates
        val getStateFromServiceIntent =
            Intent(StreamService.ACTION_GET_STATE).setPackage(fragmentActivity.packageName)
        fragmentActivity.sendBroadcast(getStateFromServiceIntent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class RadioStationActionHandler(private val radioViewModel: RadioViewModel) :
        com.smoothradio.radio.feature.discover.util.RadioStationActionHandler {
        override fun onStationSelected(station: RadioStation) {
            playerControlViewModel.requestPlayStation(station)
        }

        override fun onToggleFavorite(station: RadioStation, isFavorite: Boolean) {
            radioViewModel.toggleFavorite(station.id, isFavorite)
        }

        override fun onRequestShowToast(message: String) {
            showToast(message)
        }

        private fun showToast(message: String) {
            Toast.makeText(fragmentActivity, message, Toast.LENGTH_SHORT).show()
        }
    }
}
