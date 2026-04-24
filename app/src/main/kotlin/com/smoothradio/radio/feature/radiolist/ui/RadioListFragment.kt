package com.smoothradio.radio.feature.radiolist.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.smoothradio.radio.MainActivity
import com.smoothradio.radio.R
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.ui.PlayerControlViewModel
import com.smoothradio.radio.core.ui.RadioViewModel
import com.smoothradio.radio.core.ui.dialog.SortOption
import com.smoothradio.radio.databinding.FragmentMusicListBinding
import com.smoothradio.radio.feature.radiolist.ui.adapter.RadioListRecyclerViewAdapter
import com.smoothradio.radio.feature.radiolist.util.RadioStationActionHandler
import com.smoothradio.radio.service.StreamService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RadioListFragment : Fragment() {
    private var _binding: FragmentMusicListBinding? = null
    private val binding get() = _binding!!

    private val radioViewModel: RadioViewModel by activityViewModels()
    private val playerControlViewModel: PlayerControlViewModel by activityViewModels()

    private lateinit var radioListRecyclerViewAdapter: RadioListRecyclerViewAdapter

    private var mainActivity: MainActivity? = null
    private lateinit var fragmentActivity: FragmentActivity

    override fun onAttach(context: Context) {
        super.onAttach(context)
        fragmentActivity = context as FragmentActivity
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainActivity = requireActivity() as? MainActivity
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMusicListBinding.inflate(inflater, container, false)
        radioListRecyclerViewAdapter = RadioListRecyclerViewAdapter(
            mutableListOf(),
            RadioStationHandler()
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
                // Player state observer
                launch {
                    playerControlViewModel.playbackState.collect { state ->
                        radioListRecyclerViewAdapter.setState(state)
                        val currentStation: RadioStation? =
                            playerControlViewModel.playingStation.value
                        currentStation?.let { radioListRecyclerViewAdapter.updateStation(it) }
                    }
                }
                // Favorite stations observer
                launch {
                    radioViewModel.favoriteStations.collect { favorites ->
                        radioListRecyclerViewAdapter.updateFavorites(favorites)
                    }
                }
                launch {
//                    radioViewModel.favoriteToggleResult.collect { success ->
//                        if (!success) showToast(getString(R.string.favorite_limit_reached))
//                    }
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
//            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
//                mainActivity?.bottomSheetBehavior?.let { bottomSheetBehavior ->
//                    if (!recyclerView.canScrollVertically(1)) {
//                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
//                    } else {
//                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
//                    }
//                }
//            }
        })
    }

    override fun onResume() {
        super.onResume()
        mainActivity = requireActivity() as? MainActivity

        // Update ui or get currently playing state from service
        val intent = Intent(StreamService.ACTION_GET_STATE).apply {
            setPackage(fragmentActivity.packageName)
        }
        fragmentActivity.sendBroadcast(intent)
    }

    private fun showToast(message: String) {
        Toast.makeText(fragmentActivity, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun filterStations(query: String) {
        // Defensive check to avoid crash:
        // (e.g., when restoring state or early input from search box).
        // Ensure adapter is ready before calling filter to avoid lateinit crash.
        if (!::radioListRecyclerViewAdapter.isInitialized) {
            return
        }
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

    private inner class RadioStationHandler : RadioStationActionHandler {

        override fun onStationSelected(station: RadioStation) {
            playerControlViewModel.requestPlayStation(station)
        }

        override fun onToggleFavorite(station: RadioStation, isFavorite: Boolean) {
            radioViewModel.toggleFavorite(station.id, isFavorite)
        }

        override fun onRequestShowToast(message: String) {
            showToast(message)
        }
    }
}
