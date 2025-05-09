package com.smoothradio.radio.feature.discover.ui.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.smoothradio.radio.R
import com.smoothradio.radio.core.model.RadioStation
import com.smoothradio.radio.databinding.CategoryradioitemBinding
import com.smoothradio.radio.feature.discover.ui.DiscoverFragment
import com.smoothradio.radio.feature.radio_list.util.StationDiffUtilCallback
import com.smoothradio.radio.service.StreamService
import com.smoothradio.radio.service.StreamService.StreamStates.BUFFERING
import com.smoothradio.radio.service.StreamService.StreamStates.PLAYING
import com.smoothradio.radio.service.StreamService.StreamStates.PREPARING

class CategoryRecyclerViewAdapter(
    stationLogosList: List<RadioStation>,
    private val radioStationActionHandler: DiscoverFragment.RadioStationActionHandler
) : RecyclerView.Adapter<CategoryRecyclerViewAdapter.ItemViewViewHolder>() {

    private val radioStationItems: MutableList<RadioStation> = ArrayList(stationLogosList)
    private var lastStation: RadioStation? = null
    private var state: String = StreamService.StreamStates.ENDED

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewViewHolder {
        val binding = CategoryradioitemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ItemViewViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ItemViewViewHolder, position: Int) {
        val radioStation = radioStationItems[position]

        updateFavoriteIcon(holder, radioStation)

        holder.binding.ivCategoryLogo.setImageResource(radioStation.logoResource)
        holder.binding.tvCategoryChannelName.text = radioStation.stationName
        holder.binding.ivCategoryPlay.setOnClickListener(PlayOnclickListener(radioStation))
        holder.binding.ivCategoryFavourite.setOnClickListener(FavouriteListener(radioStation, holder))

        updatePlayer(radioStation, holder)

        if (holder.bindingAdapterPosition == RecyclerView.NO_POSITION) {
            holder.binding.clCategoryLayout.animation =
                AnimationUtils.loadAnimation(holder.itemView.context, R.anim.recyclerviewscrollanimation)
        }
    }

    override fun getItemCount(): Int = radioStationItems.size

    private fun updatePlayer(radioStation: RadioStation, holder: ItemViewViewHolder) {
        val isPlaying = radioStation.isPlaying

        if (!isPlaying) {
            holder.binding.lottieLoadingAnimation.visibility = View.INVISIBLE
            holder.binding.ivCategoryPlay.visibility = View.VISIBLE
            holder.binding.ivCategoryPlay.setImageResource(R.drawable.playicon)
            return
        }

        when (state) {
            PLAYING -> {
                holder.binding.lottieLoadingAnimation.visibility = View.INVISIBLE
                holder.binding.ivCategoryPlay.visibility = View.VISIBLE
                holder.binding.ivCategoryPlay.setImageResource(R.drawable.pauseicon)
            }
            PREPARING, BUFFERING -> {
                holder.binding.lottieLoadingAnimation.visibility = View.VISIBLE
                holder.binding.ivCategoryPlay.visibility = View.INVISIBLE
            }
            else -> {
                holder.binding.lottieLoadingAnimation.visibility = View.INVISIBLE
                holder.binding.ivCategoryPlay.visibility = View.VISIBLE
                holder.binding.ivCategoryPlay.setImageResource(R.drawable.playicon)
            }
        }
    }

    inner class ItemViewViewHolder(val binding: CategoryradioitemBinding) : RecyclerView.ViewHolder(binding.root)

    inner class PlayOnclickListener(private val radioStation: RadioStation) : View.OnClickListener {
        override fun onClick(view: View) {
            lastStation?.let { last ->
                val lastIndex = getPositionOfStation()
                if (lastIndex != RecyclerView.NO_POSITION) {
                    radioStationItems[lastIndex].isPlaying = false
                    notifyItemChanged(lastIndex)
                }
            }

            radioStationActionHandler.onStationSelected(radioStation)
        }
    }

    inner class FavouriteListener(
        private val radioStation: RadioStation,
        private val viewHolder: ItemViewViewHolder
    ) : View.OnClickListener {
        override fun onClick(view: View) {
            val isFavorite = radioStation.isFavorite
            radioStation.isFavorite = !isFavorite

            radioStationActionHandler.onToggleFavorite(radioStation, !isFavorite)
            radioStationActionHandler.onRequestshowToast(
                if (isFavorite) "Removed from favorites: ${radioStation.stationName}"
                else "Added to favorites: ${radioStation.stationName}"
            )

            updateFavoriteIcon(viewHolder, radioStation)
        }
    }

    private fun updateFavoriteIcon(holder: ItemViewViewHolder, station: RadioStation) {
        val resId = if (station.isFavorite) R.drawable.favorite_20px_filled else R.drawable.favorite_20px
        holder.binding.ivCategoryFavourite.setImageResource(resId)
    }

    fun setSelectedStationWithState(station: RadioStation, state: String) {
        this.state = state

        radioStationItems.forEach { it.isPlaying = false }

        val index = getPositionOfStationById(station.id)
        if (index != RecyclerView.NO_POSITION) {
            radioStationItems[index].isPlaying = true
        }

        notifyDataSetChanged()
    }

    private fun getPositionOfStationById(id: Int): Int {
        return radioStationItems.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: RecyclerView.NO_POSITION
    }

    private fun getPositionOfStation(): Int {
        return lastStation?.let { radioStationItems.indexOf(it) } ?: RecyclerView.NO_POSITION
    }

    interface RadioStationActionListener {
        fun onStationSelected(station: RadioStation)
        fun onToggleFavorite(station: RadioStation, isFavorite: Boolean)
        fun onRequestshowToast(message: String)
    }
}
