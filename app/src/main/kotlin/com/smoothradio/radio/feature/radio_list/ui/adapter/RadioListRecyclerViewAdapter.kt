package com.smoothradio.radio.feature.radio_list.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdRequest
import com.smoothradio.radio.R
import com.smoothradio.radio.core.domain.model.AdItem
import com.smoothradio.radio.core.domain.model.ListItem
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.databinding.AdviewBinding
import com.smoothradio.radio.databinding.EmptyFavouritiesBinding
import com.smoothradio.radio.databinding.RadioitemBinding
import com.smoothradio.radio.feature.radio_list.ui.adapter.util.StationDiffUtilCallback
import com.smoothradio.radio.feature.radio_list.ui.adapter.util.StationSortHelper
import com.smoothradio.radio.feature.radio_list.util.RadioStationActionHandler
import com.smoothradio.radio.service.StreamService.StreamStates.BUFFERING
import com.smoothradio.radio.service.StreamService.StreamStates.PLAYING
import com.smoothradio.radio.service.StreamService.StreamStates.PREPARING

/**
 * Adapter for displaying a list of radio stations in a RecyclerView.
 *
 * This adapter handles different view types for radio station items, ad items, and an empty list view.
 * It also manages filtering, sorting, and favorite functionality for the radio stations.
 *
 * @property radioStations The initial list of radio stations to display.
 * @property radioStationActionHandler A handler for actions performed on radio stations, such as selection or toggling favorites.
 */
class RadioListRecyclerViewAdapter(
    radioStations: List<RadioStation>,
    private val radioStationActionHandler: RadioStationActionHandler
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val favouriteList = mutableListOf<RadioStation>()
    private val filteredStationNameList = mutableListOf<ListItem>()

    private val stationList = radioStations.toMutableList()
    private val recyclerViewItems: MutableList<ListItem> = mutableListOf()

    private val emptyRadioStation = RadioStation(
        id = 0,
        logoResource = 0,
        stationName = "",
        frequency = "",
        location = "",
        streamLink = "",
        isPlaying = true,
        isFavorite = false
    )
    private var currentState = DisplayState.POPULAR
    private var state: String = ""
    private var lastSearchQuery: String? = null

    enum class DisplayState { POPULAR, FAVORITES, ASCENDING, DESCENDING, SEARCH }

    init {
        val displayList = if (stationList.isEmpty()) emptyList() else injectAdItems(stationList)
        recyclerViewItems.addAll(displayList)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            ITEM_VIEW -> {
                val binding =
                    RadioitemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                ItemViewViewHolder(binding)
            }

            AD_VIEW -> {
                val binding =
                    AdviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                AdViewHolder(binding)
            }

            else -> {
                val binding = EmptyFavouritiesBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                EmptyFavouriteListViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = recyclerViewItems[position]
        when {
            holder is ItemViewViewHolder && item is RadioStation -> bindRadioStation(holder, item)
            holder is AdViewHolder && item is AdItem -> bindAdView(holder)
        }
    }

    private fun bindRadioStation(holder: ItemViewViewHolder, radioStation: RadioStation) =
        with(holder.binding) {
            ivFavourite.setImageResource(
                if (radioStation.isFavorite) R.drawable.favorite_20px_filled else R.drawable.favorite_20px
            )

            ivLogo.setBackgroundResource(radioStation.logoResource)
            tvChannelName.text = radioStation.stationName
            tvFrequency.text = radioStation.frequency
            tvLocation.text = radioStation.location

            ivPlay.setImageResource(R.drawable.playicon)
            ivPlay.setOnClickListener(PlayOnclickListener(radioStation))
            ivFavourite.setOnClickListener(FavouriteListener(radioStation, holder))

            if (holder.adapterPosition == RecyclerView.NO_POSITION) {
                clLayout.animation = AnimationUtils.loadAnimation(
                    holder.itemView.context,
                    R.anim.recyclerviewscrollanimation
                )
            }

            updatePlayerUI(holder, radioStation)
        }


    private fun updatePlayerUI(holder: ItemViewViewHolder, station: RadioStation) =
        with(holder.binding) {
            when {
                !station.isPlaying -> {
                    lottieLoadingAnimation.isInvisible = true
                    ivPlay.isVisible = true
                    ivPlay.setImageResource(R.drawable.playicon)
                }

                state == PLAYING -> {
                    lottieLoadingAnimation.isInvisible = true
                    ivPlay.isVisible = true
                    ivPlay.setImageResource(R.drawable.pauseicon)
                }

                state == PREPARING || state == BUFFERING -> {
                    lottieLoadingAnimation.isVisible = true
                    ivPlay.isInvisible = true
                }

                else -> {
                    lottieLoadingAnimation.isInvisible = true
                    ivPlay.isVisible = true
                    ivPlay.setImageResource(R.drawable.playicon)
                }
            }
        }


    private fun bindAdView(holder: AdViewHolder) {
        val adRequest = AdRequest.Builder().build()
        holder.binding.adViewRecyclerviewItem.loadAd(adRequest)
    }

    override fun getItemViewType(position: Int): Int {
        val item = recyclerViewItems[position]
        return when {
            (currentState == DisplayState.SEARCH && filteredStationNameList.isEmpty()) ||
                    (currentState == DisplayState.FAVORITES && favouriteList.isEmpty()) -> EMPTY_ITEM

            item is AdItem -> AD_VIEW
            else -> ITEM_VIEW
        }
    }

    override fun getItemCount(): Int = recyclerViewItems.size

    inner class ItemViewViewHolder(val binding: RadioitemBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class AdViewHolder(val binding: AdviewBinding) : RecyclerView.ViewHolder(binding.root)
    inner class EmptyFavouriteListViewHolder(val binding: EmptyFavouritiesBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class PlayOnclickListener(private val radioStation: RadioStation) : View.OnClickListener {
        private val stationId = radioStation.id
        override fun onClick(view: View?) {
            radioStationActionHandler.onStationSelected(radioStation)
            setPlayingStation(stationId)
        }
    }

    inner class FavouriteListener(
        private val radioStation: RadioStation,
        private val holder: ItemViewViewHolder
    ) : View.OnClickListener {
        override fun onClick(view: View?) {
            val newFavoriteState = !radioStation.isFavorite
            radioStation.isFavorite = newFavoriteState
//            radioStationActionHandler.onToggleFavorite(radioStation, newFavoriteState)

            holder.binding.ivFavourite.setImageResource(
                if (newFavoriteState) R.drawable.favorite_20px_filled else R.drawable.favorite_20px
            )
        }

    }

    fun getStationAtPosition(position: Int): RadioStation {
        val item = recyclerViewItems[position]
        return if (item is RadioStation) item else emptyRadioStation
    }

    fun getPositionOfStation(stationId: Int): Int {
        val dummyStation = RadioStation(
            id = stationId,
            logoResource = 0,
            stationName = "",
            frequency = "",
            location = "",
            streamLink = "",
            isPlaying = true,
            isFavorite = false
        )
        return recyclerViewItems.indexOf(dummyStation)
    }

    fun updateStation(station: RadioStation) {
        val position = recyclerViewItems.indexOf(station)
        if (position != RecyclerView.NO_POSITION) notifyItemChanged(position)
    }

    fun setState(state: String) {
        this.state = state
    }

    fun setPlayingStation(stationId: Int) {
        val updatedList = stationList.map { station ->
            station.copy(isPlaying = station.id == stationId)
        }
        update(updatedList)
    }

    fun update(newList: List<RadioStation>) {
        stationList.clear()
        stationList.addAll(newList)

        if (currentState == DisplayState.FAVORITES) return

        val displayList: List<ListItem> = when (currentState) {
            DisplayState.SEARCH -> {
                val query = lastSearchQuery.orEmpty().lowercase()
                val refreshedList = stationList.filter {
                    it.stationName.lowercase().contains(query)
                }
                filteredStationNameList.clear()
                filteredStationNameList.addAll(refreshedList)
                filteredStationNameList
            }


            DisplayState.ASCENDING ->
                injectAdItems(stationList.sortedBy { it.stationName.lowercase() })

            DisplayState.DESCENDING ->
                injectAdItems(stationList.sortedByDescending { it.stationName.lowercase() })

            else -> injectAdItems(stationList)
        }

        if (recyclerViewItems.isEmpty()) { // for first time display
            recyclerViewItems.addAll(displayList)
            notifyDataSetChanged()
            return
        }

        val diff = DiffUtil.calculateDiff(StationDiffUtilCallback(recyclerViewItems, displayList))
        recyclerViewItems.clear()
        recyclerViewItems.addAll(displayList)
        diff.dispatchUpdatesTo(this)
    }


    fun updateFavorites(newFavorites: List<RadioStation>) {
        favouriteList.clear()
        favouriteList.addAll(newFavorites)

        if (currentState != DisplayState.FAVORITES) return

        val newList = if (favouriteList.isEmpty()) listOf(emptyRadioStation) else favouriteList
        val diff = DiffUtil.calculateDiff(StationDiffUtilCallback(recyclerViewItems, newList))
        recyclerViewItems.clear()
        recyclerViewItems.addAll(newList)
        diff.dispatchUpdatesTo(this)
    }


    fun filter(input: String?) {
        if (currentState !in listOf(DisplayState.POPULAR, DisplayState.SEARCH)) {
            sortPopular()
        }

        lastSearchQuery = input.orEmpty().trim()
        filteredStationNameList.clear()

        if (lastSearchQuery!!.isEmpty()) {
            handleEmptySearch()
        } else {
            handleSearchQuery(lastSearchQuery!!)
        }

        updateDisplayedResults()
    }


    private fun handleEmptySearch() {
        currentState = DisplayState.POPULAR
        val withAds = injectAdItems(stationList)
        filteredStationNameList.addAll(withAds)
    }

    private fun handleSearchQuery(query: String) {
        currentState = DisplayState.SEARCH
        val lowerQuery = query.lowercase()
        stationList.forEach { station ->
            if (station.stationName.lowercase().contains(lowerQuery)) {
                filteredStationNameList.add(station)
            }
        }
    }

    private fun updateDisplayedResults() {
        recyclerViewItems.clear()
        if (filteredStationNameList.isEmpty()) {
            recyclerViewItems.add(emptyRadioStation)
        } else {
            recyclerViewItems.addAll(filteredStationNameList)
        }
        notifyDataSetChanged()
    }

    fun sortAndDisplay(state: DisplayState) {
        currentState = state
        val sortedList = ArrayList(stationList)
        when (state) {
            DisplayState.ASCENDING -> StationSortHelper.sortByName(sortedList, true)
            DisplayState.DESCENDING -> StationSortHelper.sortByName(sortedList, false)
            else -> {}
        }
        val withAds = injectAdItems(sortedList)
        recyclerViewItems.clear()
        recyclerViewItems.addAll(withAds)
        notifyDataSetChanged()
    }

    fun sortPopular() {
        currentState = DisplayState.POPULAR
        val withAds = injectAdItems(stationList)
        recyclerViewItems.clear()
        recyclerViewItems.addAll(withAds)
        notifyDataSetChanged()
    }

    fun sortFavourites() {
        currentState = DisplayState.FAVORITES
        recyclerViewItems.clear()

        if (favouriteList.isEmpty()) {
            radioStationActionHandler.onRequestShowToast("No favorite stations added")
            recyclerViewItems.add(emptyRadioStation)
        } else {
            recyclerViewItems.addAll(favouriteList)
        }

        notifyDataSetChanged()
    }

    private fun injectAdItems(originalList: List<RadioStation>): List<ListItem> {
        val modifiedList = originalList.toMutableList<ListItem>()
        val adPositions = listOf(
            AD_POSITION_1,
            AD_POSITION_2,
            AD_POSITION_3,
            AD_POSITION_4,
            AD_POSITION_5,
            AD_POSITION_6
        )
        for (pos in adPositions) {
            if (pos <= modifiedList.size) modifiedList.add(pos, AdItem())
        }
        return modifiedList
    }

    private companion object {
        private const val AD_VIEW = 0
        private const val ITEM_VIEW = 1
        private const val EMPTY_ITEM = 2

        private const val AD_POSITION_1 = 1
        private const val AD_POSITION_2 = 9
        private const val AD_POSITION_3 = 36
        private const val AD_POSITION_4 = 45
        private const val AD_POSITION_5 = 54
        private const val AD_POSITION_6 = 72
    }

}
