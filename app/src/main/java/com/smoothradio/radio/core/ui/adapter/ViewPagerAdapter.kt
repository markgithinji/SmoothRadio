package com.smoothradio.radio.core.ui.adapter

import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.smoothradio.radio.feature.discover.ui.DiscoverFragment
import com.smoothradio.radio.feature.radio_list.ui.RadioListFragment
import com.smoothradio.radio.feature.player.ui.PlayerFragment
import java.lang.reflect.Field

class ViewPagerAdapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle
) : FragmentStateAdapter(fragmentManager, lifecycle) {

    private val fragmentList = mutableListOf<Fragment>()
    private var customTouchSlopFactor = 3

    fun addFragments() {
        fragmentList.add(RadioListFragment())
        fragmentList.add(PlayerFragment())
        fragmentList.add(DiscoverFragment())
    }

    private fun adjustTouchSlop() {
        try {
            val recyclerViewField: Field = ViewPager2::class.java.getDeclaredField("mRecyclerView")
            recyclerViewField.isAccessible = true
            val recyclerView = recyclerViewField.get(this) as RecyclerView

            val touchSlopField: Field = RecyclerView::class.java.getDeclaredField("mTouchSlop")
            touchSlopField.isAccessible = true
            val touchSlop = touchSlopField.get(recyclerView) as Int
            touchSlopField.set(recyclerView, touchSlop * customTouchSlopFactor)
        } catch (e: Exception) {
            Log.e("CustomViewPager2", "Error adjusting touch slop", e)
        }
    }

    fun setSwipeSensitivityFactor(factor: Int) {
        customTouchSlopFactor = factor
        adjustTouchSlop()
    }

    override fun createFragment(position: Int): Fragment = fragmentList[position]

    override fun getItemCount(): Int = fragmentList.size
}
