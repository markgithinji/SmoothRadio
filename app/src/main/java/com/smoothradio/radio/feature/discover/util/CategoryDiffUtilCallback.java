package com.smoothradio.radio.feature.discover.util;

import androidx.recyclerview.widget.DiffUtil;

import com.smoothradio.radio.core.model.Category;

import java.util.List;

public class CategoryDiffUtilCallback extends DiffUtil.Callback {

    private final List<Category> oldList;
    private final List<Category> newList;

    public CategoryDiffUtilCallback(List<Category> oldList, List<Category> newList) {
        this.oldList = oldList;
        this.newList = newList;
    }

    @Override
    public int getOldListSize() {
        return oldList.size();
    }

    @Override
    public int getNewListSize() {
        return newList.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        // Compare based on a unique identifier for each category (like the label)
        return oldList.get(oldItemPosition).getLabel().equals(newList.get(newItemPosition).getLabel());
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        // Compare the category contents (list of RadioStations in this case)
        Category oldCategory = oldList.get(oldItemPosition);
        Category newCategory = newList.get(newItemPosition);

        return oldCategory.getCategoryRadioStationList().equals(newCategory.getCategoryRadioStationList());
    }
}

