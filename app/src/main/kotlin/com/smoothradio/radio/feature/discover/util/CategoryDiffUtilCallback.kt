package com.smoothradio.radio.feature.discover.util

import androidx.recyclerview.widget.DiffUtil
import com.smoothradio.radio.core.model.Category

class CategoryDiffUtilCallback(
    private val oldList: List<Category>,
    private val newList: List<Category>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        // Compare based on unique identifier (label)
        return oldList[oldItemPosition].label == newList[newItemPosition].label
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldCategory = oldList[oldItemPosition]
        val newCategory = newList[newItemPosition]
        return oldCategory.categoryRadioStationList == newCategory.categoryRadioStationList
    }
}
