package com.smoothradio.radio.feature.radio_list.ui.adapter

import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdRequest
import com.smoothradio.radio.MainActivity
import com.smoothradio.radio.R
import com.smoothradio.radio.core.model.AdItem
import com.smoothradio.radio.core.model.ListItem
import com.smoothradio.radio.core.model.RadioStation
import com.smoothradio.radio.databinding.AdviewBinding
import com.smoothradio.radio.databinding.EmptyFavouritiesBinding
import com.smoothradio.radio.databinding.RadioitemBinding
import com.smoothradio.radio.feature.radio_list.util.StationDiffUtilCallback
import com.smoothradio.radio.feature.radio_list.util.StationSortHelper
import com.smoothradio.radio.service.StreamService.StreamStates.BUFFERING
import com.smoothradio.radio.service.StreamService.StreamStates.PLAYING
import com.smoothradio.radio.service.StreamService.StreamStates.PREPARING

class RadioListRecyclerViewAdapter(
    radioStations: List<RadioStation>,
    private val radioStationActionHandler: MainActivity.RadioStationActionHandler
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
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

    val favouriteList = mutableListOf<RadioStation>()
    private val filteredStationNameList = mutableListOf<ListItem>()

    private val stationList = radioStations.toMutableList()
    private val recyclerViewItems: MutableList<ListItem> = mutableListOf()

    private val emptyRadioStation = RadioStation(0, 0, "", "", "", "", false, false)
    private var currentState = DisplayState.POPULAR
    private var state: String = ""

    init {
        val displayList = if (stationList.isEmpty()) emptyList() else injectAdItems(stationList)
        recyclerViewItems.addAll(displayList)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            ITEM_VIEW -> {
                val binding = RadioitemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                ItemViewViewHolder(binding)
            }
            AD_VIEW -> {
                val binding = AdviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                AdViewHolder(binding)
            }
            else -> {
                val binding = EmptyFavouritiesBinding.inflate(LayoutInflater.from(parent.context), parent, false)
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

    private fun bindRadioStation(holder: ItemViewViewHolder, radioStation: RadioStation) {
        val favIconRes = if (radioStation.isFavorite) R.drawable.favorite_20px_filled else R.drawable.favorite_20px
        holder.binding.ivFavourite.setImageResource(favIconRes)

        holder.binding.ivLogo.setBackgroundResource(radioStation.logoResource)
        holder.binding.tvChannelName.text = radioStation.stationName
        holder.binding.tvFrequency.text = radioStation.frequency
        holder.binding.tvLocation.text = radioStation.location

        holder.binding.ivPlay.setImageResource(R.drawable.playicon)
        holder.binding.ivPlay.setOnClickListener(PlayOnclickListener(radioStation))
        holder.binding.ivFavourite.setOnClickListener(FavouriteListener(radioStation, holder))

        holder.binding.clLayout.animation =
            AnimationUtils.loadAnimation(holder.itemView.context, R.anim.recyclerviewscrollanimation)

        updatePlayerUI(holder, radioStation)
    }

    private fun updatePlayerUI(holder: ItemViewViewHolder, station: RadioStation) {
        val isPlaying = station.isPlaying

        when {
            !isPlaying -> {
                holder.binding.lottieLoadingAnimation.visibility = View.INVISIBLE
                holder.binding.ivPlay.visibility = View.VISIBLE
                holder.binding.ivPlay.setImageResource(R.drawable.playicon)
            }
            state == PLAYING -> {
                holder.binding.lottieLoadingAnimation.visibility = View.INVISIBLE
                holder.binding.ivPlay.visibility = View.VISIBLE
                holder.binding.ivPlay.setImageResource(R.drawable.pauseicon)
            }
            state == PREPARING || state == BUFFERING -> {
                holder.binding.lottieLoadingAnimation.visibility = View.VISIBLE
                holder.binding.ivPlay.visibility = View.INVISIBLE
            }
            else -> {
                holder.binding.lottieLoadingAnimation.visibility = View.INVISIBLE
                holder.binding.ivPlay.visibility = View.VISIBLE
                holder.binding.ivPlay.setImageResource(R.drawable.playicon)
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

    inner class ItemViewViewHolder(val binding: RadioitemBinding) : RecyclerView.ViewHolder(binding.root)
    inner class AdViewHolder(val binding: AdviewBinding) : RecyclerView.ViewHolder(binding.root)
    inner class EmptyFavouriteListViewHolder(val binding: EmptyFavouritiesBinding) : RecyclerView.ViewHolder(binding.root)

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
            val isFavorite = radioStation.isFavorite
            radioStation.isFavorite = !isFavorite
            radioStationActionHandler.onToggleFavorite(radioStation, !isFavorite)
            holder.binding.ivFavourite.setImageResource(
                if (isFavorite) R.drawable.favorite_20px else R.drawable.favorite_20px_filled
            )
        }
    }

    fun getStationAtPosition(position: Int): RadioStation {
        val item = recyclerViewItems[position]
        return if (item is RadioStation) item else emptyRadioStation
    }

    fun getPositionOfStation(stationId: Int): Int {
        val dummyStation = RadioStation(stationId, 0, "", "", "", "", true, false)
        return recyclerViewItems.indexOf(dummyStation)
    }

    fun updateStation(station: RadioStation) {
        val position = recyclerViewItems.indexOf(station)
        if (position != RecyclerView.NO_POSITION) notifyItemChanged(position)
    }

    fun listIsEmpty(): Boolean = recyclerViewItems.isEmpty()

    fun setState(state: String) {
        this.state = state
    }

    fun setPlayingStation(stationId: Int) {
        val updatedList = stationList.map { station ->
            val copy = RadioStation(station)
            copy.isPlaying = station.id == stationId
            copy
        }
        update(updatedList)
    }

    fun setFavouriteStations(favoriteStations: List<RadioStation>) {
        Log.d("RadioListRecyclerViewAdapter", "setFavouriteStations: ${favoriteStations.size}")
        favouriteList.clear()
        favouriteList.addAll(favoriteStations)
    }

    fun update(newList: List<RadioStation>) {
        stationList.clear()
        stationList.addAll(newList)

        if (currentState == DisplayState.FAVORITES) return

        val oldList = ArrayList(recyclerViewItems)
        val newListWithAds = injectAdItems(newList)

        if (recyclerViewItems.isEmpty()) {
            recyclerViewItems.addAll(newListWithAds)
            notifyDataSetChanged()
            return
        }

        val diffResult = DiffUtil.calculateDiff(StationDiffUtilCallback(oldList, newListWithAds))
        recyclerViewItems.clear()
        recyclerViewItems.addAll(newListWithAds)
        diffResult.dispatchUpdatesTo(this)
    }

    fun updateFavorites(newFavorites: List<RadioStation>) {
        favouriteList.clear()
        favouriteList.addAll(newFavorites)

        if (currentState == DisplayState.POPULAR) return

        val oldList = ArrayList(recyclerViewItems)
        val newFavoriteList: MutableList<ListItem> = newFavorites.toMutableList()

        val diffResult = DiffUtil.calculateDiff(StationDiffUtilCallback(oldList, newFavoriteList))
        recyclerViewItems.clear()
        recyclerViewItems.addAll(newFavoriteList)
        diffResult.dispatchUpdatesTo(this)
    }

    private fun injectAdItems(originalList: List<RadioStation>): List<ListItem> {
        val modifiedList = originalList.toMutableList<ListItem>()
        val adPositions = listOf(AD_POSITION_1, AD_POSITION_2, AD_POSITION_3, AD_POSITION_4, AD_POSITION_5, AD_POSITION_6)
        for (pos in adPositions) {
            if (pos <= modifiedList.size) modifiedList.add(pos, AdItem())
        }
        return modifiedList
    }

    fun filter(input: String?) {
        if (currentState != DisplayState.POPULAR && currentState != DisplayState.SEARCH) sortPopular()
        filteredStationNameList.clear()

        if (TextUtils.isEmpty(input)) {
            handleEmptySearch()
        } else {
            handleSearchQuery(input!!)
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
        if (favouriteList.isEmpty()) {
            radioStationActionHandler.onRequestshowToast("No favorite stations added")
            recyclerViewItems.clear()
            recyclerViewItems.add(emptyRadioStation)
            notifyDataSetChanged()
        } else {
            recyclerViewItems.clear()
            recyclerViewItems.addAll(favouriteList)
            notifyDataSetChanged()
        }
    }

    interface RadioStationActionListener {
        fun onStationSelected(station: RadioStation)
        fun onToggleFavorite(station: RadioStation, isFavorite: Boolean)
        fun onRequestHideKeyboard()
        fun onRequestshowToast(message: String)
    }

    enum class DisplayState { POPULAR, FAVORITES, ASCENDING, DESCENDING, SEARCH }
}
