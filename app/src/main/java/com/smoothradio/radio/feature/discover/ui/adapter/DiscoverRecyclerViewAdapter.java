package com.smoothradio.radio.feature.discover.ui.adapter;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.smoothradio.radio.core.model.Category;
import com.smoothradio.radio.core.model.RadioStation;
import com.smoothradio.radio.databinding.CategoryitemBinding;
import com.smoothradio.radio.feature.discover.ui.DiscoverFragment;
import com.smoothradio.radio.feature.discover.util.CategoryDiffUtilCallback;
import com.smoothradio.radio.service.StreamService;

import java.util.ArrayList;
import java.util.List;

public class DiscoverRecyclerViewAdapter extends RecyclerView.Adapter {
    private final List<Category> categoryList;
    private DiscoverFragment.RadioStationActionHandler radioStationActionHandler;
    private final List<CategoryRecyclerViewAdapter> categoryAdapters = new ArrayList<>();


    public DiscoverRecyclerViewAdapter(List<Category> CategoryList) {
        this.categoryList = new ArrayList<>(CategoryList);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        CategoryitemBinding categoryitemBinding = CategoryitemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new CategoryItemViewHolder(categoryitemBinding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        CategoryItemViewHolder categoryItemViewHolder = (CategoryItemViewHolder) holder;

        Category category = categoryList.get(position);

        categoryItemViewHolder.binding.tvCategoryLabel.setText(category.getLabel());

        CategoryRecyclerViewAdapter adapter = categoryAdapters.get(position);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(categoryItemViewHolder.itemView.getContext());
        linearLayoutManager.setOrientation(RecyclerView.HORIZONTAL);

        RecyclerView rvCategory = categoryItemViewHolder.binding.rvCategory;
        rvCategory.setAdapter(adapter);
        rvCategory.setLayoutManager(linearLayoutManager);
        rvCategory.setHasFixedSize(true);
        if (rvCategory.getItemDecorationCount() == 0) {
            rvCategory.addItemDecoration(new DividerItemDecoration(rvCategory.getContext(), DividerItemDecoration.VERTICAL));
        }

        categoryAdapters.add(adapter);
    }

    @Override
    public int getItemCount() {
        return categoryList.size();
    }

    public void setRadioStationActionListener(DiscoverFragment.RadioStationActionHandler radioStationActionHandler) {
        this.radioStationActionHandler = radioStationActionHandler;
    }

    public void updateCategoryList(List<Category> newCategoryList) {
        CategoryDiffUtilCallback diffUtilCallback = new CategoryDiffUtilCallback(this.categoryList, newCategoryList);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffUtilCallback);

        this.categoryList.clear();
        this.categoryList.addAll(newCategoryList);

        this.categoryAdapters.clear();
        for (Category category : newCategoryList) {
            CategoryRecyclerViewAdapter adapter =
                    new CategoryRecyclerViewAdapter(category.getCategoryRadioStationList(), radioStationActionHandler);
            this.categoryAdapters.add(adapter);
        }

        diffResult.dispatchUpdatesTo(this);
    }

    public void setSelectedStationWithState(RadioStation station, String state) {
        for (CategoryRecyclerViewAdapter categoryAdapter : categoryAdapters) {
            categoryAdapter.setSelectedStationWithState(station, state);
        }
        notifyDataSetChanged();
    }

    class CategoryItemViewHolder extends RecyclerView.ViewHolder {
        CategoryitemBinding binding;

        public CategoryItemViewHolder(@NonNull CategoryitemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

}
