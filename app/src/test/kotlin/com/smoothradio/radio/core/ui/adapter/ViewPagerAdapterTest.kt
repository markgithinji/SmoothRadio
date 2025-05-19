package com.smoothradio.radio.core.ui.adapter

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.viewpager2.widget.ViewPager2
import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.feature.discover.ui.DiscoverFragment
import com.smoothradio.radio.feature.player.ui.PlayerFragment
import com.smoothradio.radio.feature.radio_list.ui.RadioListFragment
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewPagerAdapterTest {

    private lateinit var viewPager2: ViewPager2
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ViewPagerAdapter

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        viewPager2 = ViewPager2(context)
        recyclerView = RecyclerView(context)

        // Set internal mRecyclerView via reflection
        val field = ViewPager2::class.java.getDeclaredField("mRecyclerView")
        field.isAccessible = true
        field.set(viewPager2, recyclerView)

        // Set internal mTouchSlop
        val slopField =
            RecyclerView::class.java.getDeclaredField("mTouchSlop").apply { isAccessible = true }
        slopField.set(recyclerView, 10)

        adapter = ViewPagerAdapter(
            fragmentManager = mock(),
            lifecycle = mock(),
            viewPager2 = viewPager2,
            swipeSensitivityFactor = 4
        )
    }

    @Test
    fun getItemCount_shouldReturnCorrectSize() {
        assertThat(adapter.itemCount).isEqualTo(3)
    }

    @Test
    fun createFragment_shouldReturnCorrectFragments() {
        assertThat(adapter.createFragment(0)).isInstanceOf(RadioListFragment::class.java)
        assertThat(adapter.createFragment(1)).isInstanceOf(PlayerFragment::class.java)
        assertThat(adapter.createFragment(2)).isInstanceOf(DiscoverFragment::class.java)
    }

    @Test
    fun adjustTouchSlop_shouldMultiplySlopByFactor() {
        val slopField =
            RecyclerView::class.java.getDeclaredField("mTouchSlop").apply { isAccessible = true }
        val slop = slopField.get(recyclerView) as Int
        assertThat(slop).isEqualTo(40) // 10 × 4
    }
}
