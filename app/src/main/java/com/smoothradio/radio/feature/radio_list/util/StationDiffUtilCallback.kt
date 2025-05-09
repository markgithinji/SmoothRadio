package com.smoothradio.radio.feature.radio_list.util;

import android.util.Log;

import androidx.recyclerview.widget.DiffUtil;

import com.smoothradio.radio.core.model.ListItem;
import com.smoothradio.radio.core.model.RadioStation;
import com.smoothradio.radio.core.model.AdItem;

import java.util.List;
import java.util.Objects;

public class StationDiffUtilCallback extends DiffUtil.Callback {

    private final List<ListItem> oldList;
    private final List<ListItem> newList;

    public StationDiffUtilCallback(List<ListItem> oldList, List<ListItem> newList) {
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
        ListItem oldItem = oldList.get(oldItemPosition);
        ListItem newItem = newList.get(newItemPosition);

        if (oldItem instanceof RadioStation && newItem instanceof RadioStation) {
            boolean isSame = ((RadioStation) oldItem).getId() == ((RadioStation) newItem).getId();
            Log.d("DiffUtil", "areItemsTheSame (RadioStation): " + isSame);
            return isSame;
        } else if (oldItem instanceof AdItem && newItem instanceof AdItem) {
            // treat all ads as same (or compare by index if dynamic)
            Log.d("DiffUtil", "areItemsTheSame (AdItem): true");
            return true;
        }

        return false; // different types → not same
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        ListItem oldItem = oldList.get(oldItemPosition);
        ListItem newItem = newList.get(newItemPosition);

        if (oldItem instanceof RadioStation && newItem instanceof RadioStation) {
            RadioStation oldStation = (RadioStation) oldItem;
            RadioStation newStation = (RadioStation) newItem;

            boolean isSame = Objects.equals(oldStation.getUrl(), newStation.getUrl())
                    && oldStation.isPlaying() == newStation.isPlaying()
                    && oldStation.isFavorite() == newStation.isFavorite();

            Log.d("DiffUtil", "areContentsTheSame (RadioStation): " + isSame);
            return isSame;
        } else if (oldItem instanceof AdItem && newItem instanceof AdItem) {
            Log.d("DiffUtil", "areContentsTheSame (AdItem): true");
            return true; // no content diff tracking for static ads
        }

        return false; // different types
    }
}
