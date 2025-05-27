package com.smoothradio.radio.feature.discover.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.smoothradio.radio.core.domain.model.Category
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.databinding.CategoryitemBinding
import com.smoothradio.radio.feature.discover.ui.adapter.util.CategoryDiffUtilCallback
import com.smoothradio.radio.feature.discover.util.RadioStationActionHandler

/**
 * Adapter for the RecyclerView that displays a list of categories, each containing a horizontal list of radio stations.
 *
 * This adapter manages the display of category labels and delegates the display of radio stations within each category
 * to a nested [CategoryRecyclerViewAdapter].
 *
 * @param categoryList The initial list of [Category] objects to display.
 * @param radioStationActionHandler An interface for handling actions performed on individual radio stations (e.g., play, favorite).
 */
class DiscoverRecyclerViewAdapter(
    categoryList: List<Category>,
    private val radioStationActionHandler: RadioStationActionHandler
) : RecyclerView.Adapter<DiscoverRecyclerViewAdapter.CategoryItemViewHolder>() {

    private val categoryList: MutableList<Category> = ArrayList(categoryList)
    private val categoryAdapters: MutableList<CategoryRecyclerViewAdapter> = mutableListOf()

    init {
        categoryList.forEach { category ->
            val adapter = CategoryRecyclerViewAdapter(
                category.categoryRadioStationList,
                radioStationActionHandler
            )
            categoryAdapters.add(adapter)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryItemViewHolder {
        val binding =
            CategoryitemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CategoryItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryItemViewHolder, position: Int) {
        val category = categoryList[position]
        holder.binding.tvCategoryLabel.text = category.label

        val adapter = categoryAdapters[position]
        val layoutManager = LinearLayoutManager(holder.itemView.context).apply {
            orientation = RecyclerView.HORIZONTAL
        }

        val recyclerView = holder.binding.rvCategory
        recyclerView.adapter = adapter
        recyclerView.layoutManager = layoutManager
        recyclerView.setHasFixedSize(true)
    }

    override fun getItemCount(): Int = categoryList.size

    fun updateCategoryList(newCategoryList: List<Category>) {
        val diffUtilCallback = CategoryDiffUtilCallback(this.categoryList, newCategoryList)
        val diffResult = DiffUtil.calculateDiff(diffUtilCallback)

        this.categoryList.clear()
        this.categoryList.addAll(newCategoryList)

        this.categoryAdapters.clear()
        this.categoryAdapters.addAll(
            newCategoryList.map { category ->
                CategoryRecyclerViewAdapter(
                    category.categoryRadioStationList,
                    radioStationActionHandler
                )
            }
        )

        diffResult.dispatchUpdatesTo(this)
    }

    fun setSelectedStationWithState(station: RadioStation, state: String) {
        categoryAdapters.forEach { it.setSelectedStationWithState(station, state) }
        notifyDataSetChanged()
    }

    fun updateFavorites(favorites: List<RadioStation>) {
        categoryAdapters.forEach { it.updateFavorites(favorites) }
        notifyDataSetChanged()
    }

    inner class CategoryItemViewHolder(val binding: CategoryitemBinding) :
        RecyclerView.ViewHolder(binding.root)
}
