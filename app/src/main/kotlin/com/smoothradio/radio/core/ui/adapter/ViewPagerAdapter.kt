package com.smoothradio.radio.core.ui.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.smoothradio.radio.feature.discover.ui.DiscoverFragment
import com.smoothradio.radio.feature.player.ui.PlayerFragment
import com.smoothradio.radio.feature.radiolist.ui.RadioListFragment
import timber.log.Timber

class ViewPagerAdapter(
    private val fragmentManager: FragmentManager,
    lifecycle: Lifecycle,
    private val viewPager2: ViewPager2,
    private val swipeSensitivityFactor: Int = 3,
    private val adjustTouch: Boolean = true
) : FragmentStateAdapter(fragmentManager, lifecycle) {

    private val fragmentFactories = listOf(
        { RadioListFragment() },
        { PlayerFragment() },
        { DiscoverFragment() }
    )

    private val fragmentMap = mutableMapOf<Int, Fragment>()

    init {
        if (adjustTouch) adjustTouchSlop()
    }

    override fun getItemCount(): Int = fragmentFactories.size

    override fun createFragment(position: Int): Fragment {
        return fragmentFactories[position]().also { fragment ->
            fragmentMap[position] = fragment
        }
    }

    fun getRadioListFragment(): RadioListFragment? {
        val tag = "f0" // ViewPager2 uses "f{position}" as the tag by default
        return (fragmentManager.findFragmentByTag(tag) as? RadioListFragment)
    }

    /**
     * Adjusts the touch slop of the ViewPager2's RecyclerView to make it more or less sensitive to swipes.
     *
     * Touch slop is the distance a touch can wander before it is considered a scroll or swipe.
     * By multiplying the default touch slop by [swipeSensitivityFactor], we can control
     * how easily the ViewPager2 initiates a swipe.
     *
     * This method uses reflection to access private fields of [ViewPager2] and [RecyclerView].
     * If these internal implementations change in future Android versions, this method might break.
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
            Timber.tag("ViewPagerAdapter").e(it, "Failed to adjust touch slop")
        }
    }
}

