package com.smoothradio.radio.core.util;

import android.util.Log;

import androidx.recyclerview.widget.DiffUtil;

import com.smoothradio.radio.model.RadioStation;

import java.util.List;
import java.util.Objects;

public class StationDiffUtilCallback extends DiffUtil.Callback {

    private final List<RadioStation> oldList;
    private final List<RadioStation> newList;

    public StationDiffUtilCallback(List<RadioStation> oldList, List<RadioStation> newList) {
        this.oldList = oldList;
        this.newList = newList;
    }

    @Override
    public int getOldListSize() {
        Log.d("DiffUtil", "getOldListSize " + oldList.size());
        return oldList.size();
    }

    @Override
    public int getNewListSize() {
        Log.d("DiffUtil", "getNewListSize " + newList.size());
        return newList.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        boolean isSame = oldList.get(oldItemPosition).getId() == newList.get(newItemPosition).getId();
        Log.d("DiffUtil", "isPlaying changed? " + !isSame + " for station ID " + oldList.get(oldItemPosition).getId());
        return isSame;
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        RadioStation oldItem = oldList.get(oldItemPosition);
        RadioStation newItem = newList.get(newItemPosition);

        boolean isSame = oldItem.getLogoResource() == newItem.getLogoResource()
                && Objects.equals(oldItem.getStationName(), newItem.getStationName())
                && Objects.equals(oldItem.getFrequency(), newItem.getFrequency())
                && Objects.equals(oldItem.getLocation(), newItem.getLocation())
                && Objects.equals(oldItem.getUrl(), newItem.getUrl())
                && oldItem.isPlaying() == newItem.isPlaying();

        Log.d("DiffUtil", "areContentsTheSame " + isSame);
        return isSame;
    }
}
