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
import androidx.coordinatorlayout.widget.CoordinatorLayout
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
    private var stationId: Int = 0
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

        eventIntent = Intent(StreamService.ACTION_EVENT_CHANGE).setPackage(fragmentActivity.packageName)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        setUpUI()
        setupObserver()
        return binding.root
    }

    private fun setupObserver() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                radioViewModel.playingStation.collect { radioStation ->
                    if (radioStation != null) {
                        currentStation = radioStation
                        stationId = radioStation.id
                        binding.ivLargeLogo.setImageResource(radioStation.logoResource)
                        binding.tvStationNamePlayerFrag.text = radioStation.stationName
                    } else {
                        getLatestStationUsingSavedId()
                        // Handle null case UI updates here if needed
                    }
                }
            }
        }
    }

    private fun getLatestStationUsingSavedId() {
        currentStation = RadioStation(stationId, 0, "", "", "", "", true, false)
        val position = mainActivity.radioListRecyclerViewAdapter.getPositionOfStation(stationId)
        if (!(mainActivity.radioListRecyclerViewAdapter.listIsEmpty() || position == RecyclerView.NO_POSITION)) {
            currentStation = mainActivity.radioListRecyclerViewAdapter.getStationAtPosition(position)
        }
    }

    private fun registerBroadcasts() {
        val eventFilter = IntentFilter().apply {
            addAction(StreamService.ACTION_EVENT_CHANGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            fragmentActivity.registerReceiver(EventReceiver(), eventFilter, Context.RECEIVER_NOT_EXPORTED)
            fragmentActivity.registerReceiver(MetadataReceiver(), IntentFilter(StreamService.ACTION_METADATA_CHANGE), Context.RECEIVER_NOT_EXPORTED)
        } else {
            fragmentActivity.registerReceiver(EventReceiver(), eventFilter)
            fragmentActivity.registerReceiver(MetadataReceiver(), IntentFilter(StreamService.ACTION_METADATA_CHANGE))
        }
    }

    override fun onResume() {
        super.onResume()
        if (mainActivity == null) {
            mainActivity = requireActivity() as MainActivity
        }

        if (playerManager.isShowingAd) {
            state = StreamService.StreamStates.PREPARING
            broadcastState(state)
        } else {
            val getStateFromServiceIntent = Intent(StreamService.ACTION_GET_STATE).setPackage(fragmentActivity.packageName)
            fragmentActivity.sendBroadcast(getStateFromServiceIntent)
        }
    }

    private fun setUpUI() {
        val adRequest = AdRequest.Builder().build()
        binding.adView.loadAd(adRequest)

        binding.ivPlayButton.setOnClickListener(PlayButtonListener())
        binding.ivRefresh.setOnClickListener(Refresh())
        binding.ivSetTimer.setOnClickListener(SetTimerOnclickListener())
    }

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
            radioViewModel.setSelectedStation(currentStation!!)
            playerManager.playOrStop()
        }
    }

    inner class SetTimerOnclickListener : View.OnClickListener {
        override fun onClick(view: View?) {
            TimerSetterHelper(fragmentActivity, coordinatorLayout)
        }
    }

    private fun updateUI() {
        TransitionManager.beginDelayedTransition(binding.playerFrag)

        binding.tvProgress.text = state
        binding.lottieLoadingAnimation.visibility = View.INVISIBLE
        binding.equalizerAnimation.visibility = View.INVISIBLE
        binding.tvMetadata.text = ""
        binding.ivPlayButton.setImageResource(R.drawable.playerfragplayicon)

        when (state) {
            StreamService.StreamStates.PREPARING -> {
                binding.lottieLoadingAnimation.visibility = View.VISIBLE
            }
            StreamService.StreamStates.PLAYING -> {
                binding.equalizerAnimation.visibility = View.VISIBLE
                binding.ivPlayButton.setImageResource(R.drawable.playerfragpauseicon)
            }
            StreamService.StreamStates.BUFFERING -> {
                binding.lottieLoadingAnimation.visibility = View.VISIBLE
            }
            StreamService.StreamStates.ENDED,
            StreamService.StreamStates.IDLE -> { }
            else -> {
                binding.tvProgress.text = ""
                binding.tvMetadata.text = ""
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
