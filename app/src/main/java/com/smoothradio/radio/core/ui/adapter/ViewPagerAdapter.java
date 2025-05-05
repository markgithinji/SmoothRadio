package com.smoothradio.radio.core.ui.adapter;


import android.util.Log;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.smoothradio.radio.feature.discover.ui.DiscoverFragment;
import com.smoothradio.radio.feature.radio_list.ui.RadioListFragment;
import com.smoothradio.radio.feature.player.ui.PlayerFragment;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class ViewPagerAdapter extends FragmentStateAdapter {
    List<Fragment>fragmentList= new ArrayList<>();
    private int customTouchSlopFactor = 3;

    public ViewPagerAdapter(@NonNull FragmentManager fragmentManager, @NonNull Lifecycle lifecycle) {
        super(fragmentManager, lifecycle);
    }


    public void addFragments()
    {
        fragmentList.add(new RadioListFragment());
        fragmentList.add(new PlayerFragment());
        fragmentList.add(new DiscoverFragment());
    }
    private void adjustTouchSlop() {
        try {
            Field recyclerViewField = ViewPager2.class.getDeclaredField("mRecyclerView");
            recyclerViewField.setAccessible(true);
            RecyclerView recyclerView = (RecyclerView) recyclerViewField.get(this);

            Field touchSlopField = RecyclerView.class.getDeclaredField("mTouchSlop");
            touchSlopField.setAccessible(true);
            int touchSlop = (int) touchSlopField.get(recyclerView);
            touchSlopField.set(recyclerView, touchSlop * customTouchSlopFactor);
        } catch (Exception e) {
            Log.e("CustomViewPager2", "Error adjusting touch slop", e);
        }
    }

    // Add a public method to change the sensitivity dynamically if needed
    public void setSwipeSensitivityFactor(int factor) {
        customTouchSlopFactor = factor;
        adjustTouchSlop();
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return fragmentList.get(position);
    }

    @Override
    public int getItemCount() {
        return fragmentList.size();
    }
}
