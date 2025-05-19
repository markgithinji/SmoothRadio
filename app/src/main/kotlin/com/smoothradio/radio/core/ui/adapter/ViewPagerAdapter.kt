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

class ViewPagerAdapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle,
    private val viewPager2: ViewPager2,
    private val swipeSensitivityFactor: Int = 3
) : FragmentStateAdapter(fragmentManager, lifecycle) {

    private val fragments = listOf(
        RadioListFragment(),
        PlayerFragment(),
        DiscoverFragment()
    )

    init {
        adjustTouchSlop()
    }

    private fun adjustTouchSlop() {
        runCatching {
            val recyclerViewField = ViewPager2::class.java.getDeclaredField("mRecyclerView").apply {
                isAccessible = true
            }
            val recyclerView = recyclerViewField.get(viewPager2) as RecyclerView

            val touchSlopField = RecyclerView::class.java.getDeclaredField("mTouchSlop").apply {
                isAccessible = true
            }
            val touchSlop = touchSlopField.get(recyclerView) as Int
            touchSlopField.set(recyclerView, touchSlop * swipeSensitivityFactor)
        }.onFailure {
            Log.e("ViewPagerAdapter", "Failed to adjust touch slop", it)
        }
    }

    override fun createFragment(position: Int): Fragment = fragments[position]

    override fun getItemCount(): Int = fragments.size
}
