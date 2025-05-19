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
import com.smoothradio.radio.feature.discover.util.CategoryDiffUtilCallback

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
        val recyclerView = holder.binding.rvCategory

        holder.binding.tvCategoryLabel.text = category.label

        recyclerView.takeIf { it.adapter == null }?.apply {
            adapter = categoryAdapters[position]
            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            setHasFixedSize(true)
            if (itemDecorationCount == 0)
                addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))

        }

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
                    radioStationActionHandler!!
                )
            }
        )

        diffResult.dispatchUpdatesTo(this)
    }

    fun setSelectedStationWithState(station: RadioStation, state: String) {
        categoryAdapters.forEach { it.setSelectedStationWithState(station, state) }
        notifyDataSetChanged()
    }


    inner class CategoryItemViewHolder(val binding: CategoryitemBinding) :
        RecyclerView.ViewHolder(binding.root)
}
