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
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.ads.AdRequest
import com.smoothradio.radio.R
import com.smoothradio.radio.core.ui.PlayerControlViewModel
import com.smoothradio.radio.databinding.FragmentPlayerBinding
import com.smoothradio.radio.feature.player.util.TimerSetterHelper
import com.smoothradio.radio.service.StreamService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PlayerFragment : Fragment() {
    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private lateinit var fragmentActivity: FragmentActivity

    private val playerControlViewModel: PlayerControlViewModel by activityViewModels()

    private val metadataReceiver: BroadcastReceiver = MetadataReceiver()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        fragmentActivity = context as FragmentActivity
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerBroadcasts()
    }

    private fun registerBroadcasts() {
        val metadataFilter = IntentFilter().apply {
            addAction(StreamService.ACTION_METADATA_CHANGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            fragmentActivity.registerReceiver(
                metadataReceiver,
                metadataFilter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
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
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch {
                    playerControlViewModel.playingStation.collect { radioStation ->
                        radioStation?.let { station ->
                            with(binding) {
                                ivLargeLogo.setImageResource(station.logoResource)
                                tvStationNamePlayerFrag.text = station.stationName
                            }
                        }
                    }
                }
                launch {
                    playerControlViewModel.playbackState.collect { state ->
                        updateUI(state)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Update ui or get currently playing state from service
        val intent = Intent(StreamService.ACTION_GET_STATE).apply {
            setPackage(fragmentActivity.packageName)
        }
        fragmentActivity.sendBroadcast(intent)
    }

    private fun updateUI(state: String) = with(binding) {
        TransitionManager.beginDelayedTransition(playerFrag)

        lottieLoadingAnimation.isInvisible = true
        equalizerAnimation.isInvisible = true
        tvMetadata.text = ""
        ivPlayButton.setImageResource(R.drawable.playerfragplayicon)

        when (state) {
            StreamService.StreamStates.PREPARING,
            StreamService.StreamStates.BUFFERING -> {
                tvProgress.text = state
                lottieLoadingAnimation.isVisible = true
            }

            StreamService.StreamStates.PLAYING -> {
                tvProgress.text = state
                equalizerAnimation.isVisible = true
                ivPlayButton.setImageResource(R.drawable.playerfragpauseicon)
            }

            StreamService.StreamStates.ENDED,
            StreamService.StreamStates.IDLE -> {
                tvProgress.text = ""
            }

            else -> {
                tvProgress.text = ""
                tvMetadata.text = ""
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        fragmentActivity.unregisterReceiver(metadataReceiver)
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

    inner class Refresh : View.OnClickListener {
        override fun onClick(view: View?) {
            playerControlViewModel.requestRefresh()
        }
    }

    inner class PlayButtonListener : View.OnClickListener {
        override fun onClick(view: View?) {
            val station = playerControlViewModel.playingStation.value ?: return
            playerControlViewModel.requestPlayStation(station)
        }
    }

    inner class SetTimerOnclickListener : View.OnClickListener {
        override fun onClick(view: View?) {
            TimerSetterHelper(fragmentActivity, binding.playerFrag).showTimerPicker()
        }
    }
}
