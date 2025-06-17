package com.smoothradio.radio.feature.discover.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.smoothradio.radio.R
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.databinding.CategoryradioitemBinding
import com.smoothradio.radio.feature.discover.util.RadioStationActionHandler
import com.smoothradio.radio.feature.radio_list.ui.adapter.util.StationDiffUtilCallback
import com.smoothradio.radio.service.StreamService
import com.smoothradio.radio.service.StreamService.StreamStates.BUFFERING
import com.smoothradio.radio.service.StreamService.StreamStates.PLAYING
import com.smoothradio.radio.service.StreamService.StreamStates.PREPARING

/**
 * Adapter for displaying a list of radio stations in a nested RecyclerView.
 * This adapter handles the display of station logos, names, play/pause buttons,
 * and favorite icons. It also manages the state of the currently playing station
 * and updates the UI accordingly.
 *
 * @property stationLogosList The initial list of [RadioStation] objects to display.
 * @property radioStationActionHandler An instance of [RadioStationActionHandler] to
 *                                    handle user interactions with the radio stations,
 *                                    such as selecting a station or toggling its favorite status.
 */
class CategoryRecyclerViewAdapter(
    radioStationList: List<RadioStation>,
    private val radioStationActionHandler: RadioStationActionHandler
) : RecyclerView.Adapter<CategoryRecyclerViewAdapter.ItemViewViewHolder>() {

    private val radioStationItems: MutableList<RadioStation> = ArrayList(radioStationList)
    private var state: String = StreamService.StreamStates.ENDED

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewViewHolder {
        val binding =
            CategoryradioitemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ItemViewViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ItemViewViewHolder, position: Int) {
        val radioStation = radioStationItems[position]
        val binding = holder.binding

        updateFavoriteIcon(radioStation, holder)
        updatePlayer(radioStation, holder)

        with(binding) {
            ivCategoryLogo.setImageResource(radioStation.logoResource)
            tvCategoryChannelName.text = radioStation.stationName
            ivCategoryPlay.setOnClickListener(PlayOnclickListener(radioStation))
            ivCategoryFavourite.setOnClickListener(FavouriteListener(radioStation))
        }

        if (holder.adapterPosition == RecyclerView.NO_POSITION) {
            holder.binding.clCategoryLayout.animation =
                AnimationUtils.loadAnimation(
                    holder.itemView.context,
                    R.anim.recyclerviewscrollanimation
                )
        }
    }

    private fun updatePlayer(radioStation: RadioStation, holder: ItemViewViewHolder) =
        with(holder.binding) {
            if (!radioStation.isPlaying) {
                lottieLoadingAnimation.isInvisible = true
                ivCategoryPlay.isVisible = true
                ivCategoryPlay.setImageResource(R.drawable.playicon)
                return
            }

            when (state) {
                PLAYING -> {
                    lottieLoadingAnimation.isInvisible = true
                    ivCategoryPlay.isVisible = true
                    ivCategoryPlay.setImageResource(R.drawable.pauseicon)
                }

                PREPARING, BUFFERING -> {
                    lottieLoadingAnimation.isVisible = true
                    ivCategoryPlay.isInvisible = true
                }

                else -> {
                    lottieLoadingAnimation.isInvisible = true
                    ivCategoryPlay.isVisible = true
                    ivCategoryPlay.setImageResource(R.drawable.playicon)
                }
            }
        }

    private fun updateFavoriteIcon(station: RadioStation, holder: ItemViewViewHolder) {
        val resId =
            if (station.isFavorite) R.drawable.favorite_20px_filled else R.drawable.favorite_20px
        holder.binding.ivCategoryFavourite.setImageResource(resId)
    }

    private inner class PlayOnclickListener(private val radioStation: RadioStation) :
        View.OnClickListener {
        override fun onClick(view: View) {
            radioStationActionHandler.onStationSelected(radioStation)
        }
    }

    /**
     * An inner class that implements the {@link View.OnClickListener} interface.
     * It is used to handle click events on favourite icon that represents a radio station's favorite status.
     * When the icon is clicked, this listener toggles the favorite status of the associated
     * {@link RadioStation} and notifies the {@link RadioStationActionHandler}.
     */
    private inner class FavouriteListener(
        private val radioStation: RadioStation,
    ) : View.OnClickListener {
        override fun onClick(view: View) {
            val isFavorite = radioStation.isFavorite

            radioStationActionHandler.apply {
                onToggleFavorite(radioStation, !isFavorite)
            }

        }
    }

    override fun getItemCount(): Int = radioStationItems.size

    inner class ItemViewViewHolder(val binding: CategoryradioitemBinding) :
        RecyclerView.ViewHolder(binding.root)

    fun updateStations(newStations: List<RadioStation>) {
        val diffCallback = StationDiffUtilCallback(radioStationItems, newStations)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        radioStationItems.clear()
        radioStationItems.addAll(newStations)

        diffResult.dispatchUpdatesTo(this)
    }


    fun setSelectedStationWithState(station: RadioStation, state: String) {
        this.state = state

        radioStationItems.forEach { it.isPlaying = false }

        val index = getPositionOfStationById(station.id)
        if (index != RecyclerView.NO_POSITION) radioStationItems[index].isPlaying = true

        notifyDataSetChanged()
    }

    fun updateFavorites(favorites: List<RadioStation>) {
        radioStationItems.forEach { station ->
            station.isFavorite = favorites.any { it.id == station.id }
        }
        notifyDataSetChanged()
    }

    private fun getPositionOfStationById(id: Int): Int {
        return radioStationItems.indexOfFirst { it.id == id }.takeIf { it >= 0 }
            ?: RecyclerView.NO_POSITION
    }

}
