package com.smoothradio.radio.feature.player.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.ads.AdRequest
import com.smoothradio.radio.MainActivity
import com.smoothradio.radio.R
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.ui.RadioViewModel
import com.smoothradio.radio.core.util.PlayerManager
import com.smoothradio.radio.databinding.FragmentPlayerBinding
import com.smoothradio.radio.feature.player.util.TimerSetterHelper
import com.smoothradio.radio.service.StreamService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlayerFragment : Fragment() {

    @Inject
    lateinit var playerManager: PlayerManager

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private var mainActivity: MainActivity? = null
    private lateinit var fragmentActivity: FragmentActivity

    private lateinit var radioViewModel: RadioViewModel

    private lateinit var eventIntent: Intent

    private var currentStation: RadioStation? = null
    private var state: String = ""

    private val eventReceiver: BroadcastReceiver = EventReceiver()
    private val metadataReceiver: BroadcastReceiver = MetadataReceiver()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mainActivity = requireActivity() as? MainActivity
        fragmentActivity = context as FragmentActivity
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        radioViewModel = ViewModelProvider(fragmentActivity)[RadioViewModel::class.java]
        eventIntent =
            Intent(StreamService.ACTION_EVENT_CHANGE).setPackage(fragmentActivity.packageName)
        registerBroadcasts()
    }

    private fun registerBroadcasts() {
        val eventFilter = IntentFilter().apply {
            addAction(StreamService.ACTION_EVENT_CHANGE)
        }
        val metadataFilter = IntentFilter().apply {
            addAction(StreamService.ACTION_METADATA_CHANGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            fragmentActivity.registerReceiver(
                eventReceiver,
                eventFilter,
                Context.RECEIVER_NOT_EXPORTED
            )
            fragmentActivity.registerReceiver(
                metadataReceiver,
                metadataFilter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            fragmentActivity.registerReceiver(eventReceiver, eventFilter)
            fragmentActivity.registerReceiver(metadataReceiver, metadataFilter)
        }
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

    private fun setUpUI() = with(binding) {
        adView.loadAd(AdRequest.Builder().build())

        ivPlayButton.setOnClickListener(PlayButtonListener())
        ivRefresh.setOnClickListener(Refresh())
        ivSetTimer.setOnClickListener(SetTimerOnclickListener())
    }

    private fun collectFlows() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    radioViewModel.playingStation.collect { radioStation ->
                        radioStation?.let { station ->
                            currentStation = station
                            playerManager.setRadioStation(station)

                            with(binding) {
                                ivLargeLogo.setImageResource(station.logoResource)
                                tvStationNamePlayerFrag.text = station.stationName
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        playerManager.bindActivity(fragmentActivity)

        // update ui or get currently playing state from service
        val isShowingAd = playerManager.isShowingAd
        if (isShowingAd) {
            state = StreamService.StreamStates.PREPARING
            broadcastState(state)
        } else {
            val getStateFromServiceIntent =
                Intent(StreamService.ACTION_GET_STATE).setPackage(requireContext().packageName)
            requireContext().sendBroadcast(getStateFromServiceIntent)
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
                StreamService.StreamStates.IDLE -> { /* no-op */}

                else -> {
                    tvProgress.text = ""
                    tvMetadata.text = ""
                }
            }
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
            TimerSetterHelper(fragmentActivity, binding.playerFrag).showTimerPicker()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        fragmentActivity.unregisterReceiver(eventReceiver)
        fragmentActivity.unregisterReceiver(metadataReceiver)
        playerManager.unregisterBroadcastReceiver()
    }
}
