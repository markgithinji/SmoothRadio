package com.smoothradio.radio.core.ui;


import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.smoothradio.radio.feature.discover.DiscoverFragment;
import com.smoothradio.radio.feature.radio_list.RadioListFragment;
import com.smoothradio.radio.feature.player.PlayerFragment;

import java.util.ArrayList;
import java.util.List;

public class ViewPagerAdapter extends FragmentStateAdapter {
    List<Fragment>fragmentList= new ArrayList<>();

    public ViewPagerAdapter(@NonNull FragmentManager fragmentManager, @NonNull Lifecycle lifecycle) {
        super(fragmentManager, lifecycle);

    }


    public void addFragments()
    {
        fragmentList.add(new RadioListFragment());
        fragmentList.add(new PlayerFragment());
        fragmentList.add(new DiscoverFragment());
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
