package com.smoothradio.radio.core.ui.adapter

import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.smoothradio.radio.feature.discover.ui.DiscoverFragment
import com.smoothradio.radio.feature.player.ui.PlayerFragment
import com.smoothradio.radio.feature.radiolist.ui.RadioListFragment

class ViewPagerAdapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle,
    private val viewPager2: ViewPager2,
    private val swipeSensitivityFactor: Int = 3,
    private val adjustTouch: Boolean = true
) : FragmentStateAdapter(fragmentManager, lifecycle) {

    private val fragments = listOf(
        RadioListFragment(),
        PlayerFragment(),
        DiscoverFragment()
    )

    init {
        if (adjustTouch) adjustTouchSlop()
    }

    /**
     * Adjusts the touch slop of the ViewPager2's RecyclerView to make it more or less sensitive to swipes.
     *
     * <p>Touch slop is the distance a touch can wander before it is considered a scroll or swipe.
     * By multiplying the default touch slop by {@code swipeSensitivityFactor}, we can control
     * how easily the ViewPager2 initiates a swipe.
     *
     * <p>This method uses reflection to access private fields of {@link ViewPager2} and {@link RecyclerView}.
     * If these internal implementations change in future Android versions, this method might break.
     * It includes error handling to log failures if reflection fails.
     *
     * <p><b>Note:</b> Modifying internal framework behavior using reflection is generally discouraged
     * as it can lead to instability and compatibility issues across different Android versions
     * or devices. Use this approach with caution and be prepared for potential breakage.
     */
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

    fun getRadioListFragment(): RadioListFragment? {
        return fragments.getOrNull(0) as? RadioListFragment
    }

    override fun createFragment(position: Int): Fragment = fragments[position]

    override fun getItemCount(): Int = fragments.size
}
