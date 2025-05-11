package com.smoothradio.radio.feature.radio_list.util

import android.util.Log
import androidx.recyclerview.widget.DiffUtil
import com.smoothradio.radio.core.model.ListItem
import com.smoothradio.radio.core.model.RadioStation
import com.smoothradio.radio.core.model.AdItem

class StationDiffUtilCallback(
    private val oldList: List<ListItem>,
    private val newList: List<ListItem>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int {
        return oldList.size
    }

    override fun getNewListSize(): Int {
        return newList.size
    }

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldList[oldItemPosition]
        val newItem = newList[newItemPosition]

        return when {
            oldItem is RadioStation && newItem is RadioStation -> {
                val isSame = oldItem.id == newItem.id
                isSame
            }
            oldItem is AdItem && newItem is AdItem -> {
                true
            }
            else -> false
        }
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldList[oldItemPosition]
        val newItem = newList[newItemPosition]

        return when {
            oldItem is RadioStation && newItem is RadioStation -> {
                val isSame = oldItem.url == newItem.url &&
                        oldItem.isPlaying == newItem.isPlaying &&
                        oldItem.isFavorite == newItem.isFavorite
                isSame
            }
            oldItem is AdItem && newItem is AdItem -> {
                true
            }
            else -> false
        }
    }
}
