package com.smoothradio.radio.feature.player.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.transition.TransitionManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdRequest
import com.smoothradio.radio.MainActivity
import com.smoothradio.radio.R
import com.smoothradio.radio.core.model.RadioStation
import com.smoothradio.radio.core.ui.RadioViewModel
import com.smoothradio.radio.core.util.PlayerManager
import com.smoothradio.radio.databinding.FragmentPlayerBinding
import com.smoothradio.radio.feature.player.util.TimerSetterHelper
import com.smoothradio.radio.service.StreamService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PlayerFragment : Fragment() {

    private lateinit var mainActivity: MainActivity
    private var currentStation: RadioStation? = null
    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private var state: String = ""
    private lateinit var coordinatorLayout: CoordinatorLayout
    private lateinit var eventIntent: Intent

    private lateinit var fragmentActivity: FragmentActivity
    private lateinit var radioViewModel: RadioViewModel
    private lateinit var playerManager: PlayerManager

    override fun onAttach(context: Context) {
        super.onAttach(context)
        fragmentActivity = context as FragmentActivity
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainActivity = requireActivity() as MainActivity
        radioViewModel = ViewModelProvider(fragmentActivity)[RadioViewModel::class.java]
        playerManager = mainActivity.playerManager

        registerBroadcasts()

        eventIntent =
            Intent(StreamService.ACTION_EVENT_CHANGE).setPackage(fragmentActivity.packageName)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        setUpUI()
        collectFlows()
        return binding.root
    }

    private fun collectFlows() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                radioViewModel.playingStation.collect { radioStation ->
                    currentStation = radioStation ?: getDefaultStation()

                    currentStation?.let { station ->
                        binding.apply {
                            ivLargeLogo.setImageResource(station.logoResource)
                            tvStationNamePlayerFrag.text = station.stationName
                        }
                    }
                }
            }
        }
    }

    private fun getDefaultStation(): RadioStation {
        val stationId = 0
        val position = mainActivity.radioListRecyclerViewAdapter.getPositionOfStation(stationId)
        if (!(mainActivity.radioListRecyclerViewAdapter.listIsEmpty() || position == RecyclerView.NO_POSITION)) {
                val station =mainActivity.radioListRecyclerViewAdapter.getStationAtPosition(position)
            return station
        }
        return RadioStation(0, 0, "", "", "", "", true,false)
    }

    private fun registerBroadcasts() {
        val eventFilter = IntentFilter().apply {
            addAction(StreamService.ACTION_EVENT_CHANGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            fragmentActivity.registerReceiver(
                EventReceiver(),
                eventFilter,
                Context.RECEIVER_NOT_EXPORTED
            )
            fragmentActivity.registerReceiver(
                MetadataReceiver(),
                IntentFilter(StreamService.ACTION_METADATA_CHANGE),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            fragmentActivity.registerReceiver(EventReceiver(), eventFilter)
            fragmentActivity.registerReceiver(
                MetadataReceiver(),
                IntentFilter(StreamService.ACTION_METADATA_CHANGE)
            )
        }
    }

    override fun onResume() {
        super.onResume()
        mainActivity = mainActivity ?: (requireActivity() as MainActivity)


        if (playerManager.isShowingAd) {
            state = StreamService.StreamStates.PREPARING
            broadcastState(state)
        } else {
            val getStateFromServiceIntent =
                Intent(StreamService.ACTION_GET_STATE).setPackage(fragmentActivity.packageName)
            fragmentActivity.sendBroadcast(getStateFromServiceIntent)
        }
    }

    private fun setUpUI() = with(binding) {
        adView.loadAd(AdRequest.Builder().build())

        ivPlayButton.setOnClickListener(PlayButtonListener())
        ivRefresh.setOnClickListener(Refresh())
        ivSetTimer.setOnClickListener(SetTimerOnclickListener())
    }

    /**
     * `MetadataReceiver` is a `BroadcastReceiver` responsible for receiving and displaying metadata
     * information about the currently playing audio stream.  It listens for broadcasts from
     * `StreamService` containing the title of the current stream and updates the UI accordingly.
     * <p>
     * Specifically, it receives an intent with the extra `StreamService.EXTRA_TITLE`, extracts the
     * title string, truncates it to a maximum of 70 characters, and updates a `TextView` with the
     * truncated title.  It also uses a `TransitionManager` to animate the UI update for a smoother
     * user experience.
     */
    inner class MetadataReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val metadata = intent?.getStringExtra(StreamService.EXTRA_TITLE)
            metadata?.let {
                val truncatedMetadata = it.substring(0, minOf(it.length, 70))
                binding.tvMetadata.text = truncatedMetadata
                TransitionManager.beginDelayedTransition(binding.playerFrag)
            }
        }
    }

    inner class EventReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            state = intent?.getStringExtra(StreamService.EXTRA_STATE) ?: ""
            updateUI()
        }
    }

    inner class Refresh : View.OnClickListener {
        override fun onClick(view: View?) {
            playerManager.refresh()
        }
    }

    inner class PlayButtonListener : View.OnClickListener {
        override fun onClick(view: View?) {
            val station = currentStation ?: return

            radioViewModel.setSelectedStation(station)
        }
    }

    inner class SetTimerOnclickListener : View.OnClickListener {
        override fun onClick(view: View?) {
            TimerSetterHelper(fragmentActivity, coordinatorLayout)
        }
    }

    private fun updateUI() = with(binding) {
        TransitionManager.beginDelayedTransition(playerFrag)

        tvProgress.text = state
        lottieLoadingAnimation.isInvisible = true
        equalizerAnimation.isInvisible = true
        tvMetadata.text = ""
        ivPlayButton.setImageResource(R.drawable.playerfragplayicon)

        when (state) {
            StreamService.StreamStates.PREPARING,
            StreamService.StreamStates.BUFFERING -> {
                lottieLoadingAnimation.isVisible = true
            }

            StreamService.StreamStates.PLAYING -> {
                equalizerAnimation.isVisible = true
                ivPlayButton.setImageResource(R.drawable.playerfragpauseicon)
            }

            StreamService.StreamStates.ENDED,
            StreamService.StreamStates.IDLE -> { /* no-op */
            }

            else -> {
                tvProgress.text = ""
                tvMetadata.text = ""
            }
        }
    }


    private fun broadcastState(state: String) {
        eventIntent.putExtra(StreamService.EXTRA_STATE, state)
        fragmentActivity.sendBroadcast(eventIntent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
