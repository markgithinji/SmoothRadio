package com.smoothradio.radio.feature.discover.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.smoothradio.radio.core.model.Category
import com.smoothradio.radio.core.model.RadioStation
import com.smoothradio.radio.databinding.CategoryitemBinding
import com.smoothradio.radio.feature.discover.ui.DiscoverFragment
import com.smoothradio.radio.feature.discover.util.CategoryDiffUtilCallback
import com.smoothradio.radio.service.StreamService

class DiscoverRecyclerViewAdapter(
    categoryList: List<Category>
) : RecyclerView.Adapter<DiscoverRecyclerViewAdapter.CategoryItemViewHolder>() {

    private val categoryList: MutableList<Category> = ArrayList(categoryList)
    private val categoryAdapters: MutableList<CategoryRecyclerViewAdapter> = mutableListOf()
    private var radioStationActionHandler: DiscoverFragment.RadioStationActionHandler? = null

    init {
        categoryList.forEach { category ->
            val adapter = CategoryRecyclerViewAdapter(category.categoryRadioStationList, radioStationActionHandler!!)
            categoryAdapters.add(adapter)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryItemViewHolder {
        val binding = CategoryitemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
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
        if (recyclerView.itemDecorationCount == 0) {
            recyclerView.addItemDecoration(
                DividerItemDecoration(recyclerView.context, DividerItemDecoration.VERTICAL)
            )
        }
    }

    override fun getItemCount(): Int = categoryList.size

    fun setRadioStationActionListener(radioStationActionHandler: DiscoverFragment.RadioStationActionHandler) {
        this.radioStationActionHandler = radioStationActionHandler
    }

    fun updateCategoryList(newCategoryList: List<Category>) {
        val diffUtilCallback = CategoryDiffUtilCallback(this.categoryList, newCategoryList)
        val diffResult = DiffUtil.calculateDiff(diffUtilCallback)

        this.categoryList.clear()
        this.categoryList.addAll(newCategoryList)

        this.categoryAdapters.clear()
        for (category in newCategoryList) {
            val adapter = CategoryRecyclerViewAdapter(category.categoryRadioStationList, radioStationActionHandler!!)
            this.categoryAdapters.add(adapter)
        }

        diffResult.dispatchUpdatesTo(this)
    }

    fun setSelectedStationWithState(station: RadioStation, state: String) {
        for (adapter in categoryAdapters) {
            adapter.setSelectedStationWithState(station, state)
        }
        notifyDataSetChanged()
    }

    inner class CategoryItemViewHolder(val binding: CategoryitemBinding) : RecyclerView.ViewHolder(binding.root)
}
