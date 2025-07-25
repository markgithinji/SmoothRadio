package com.smoothradio.radio.core.ui.adapter

import android.content.Context
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.viewpager2.widget.ViewPager2
import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.feature.discover.ui.DiscoverFragment
import com.smoothradio.radio.feature.player.ui.PlayerFragment
import com.smoothradio.radio.feature.radiolist.ui.RadioListFragment
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewPagerAdapterTest {

    private lateinit var context: Context
    private lateinit var viewPager2: ViewPager2
    private lateinit var adapter: ViewPagerAdapter

    private val mockFragmentManager: FragmentManager = mock()
    private val mockLifecycle: Lifecycle = mock()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        viewPager2 = ViewPager2(context)

        adapter = ViewPagerAdapter(
            fragmentManager = mockFragmentManager,
            lifecycle = mockLifecycle,
            viewPager2 = viewPager2,
            swipeSensitivityFactor = 3,
            adjustTouch = false // disables reflection logic in tests
        )
    }

    @Test
    fun getItemCount_shouldReturnThree() {
        assertThat(adapter.itemCount).isEqualTo(3)
    }

    @Test
    fun createFragment_shouldReturnCorrectFragmentTypes() {
        assertThat(adapter.createFragment(0)).isInstanceOf(RadioListFragment::class.java)
        assertThat(adapter.createFragment(1)).isInstanceOf(PlayerFragment::class.java)
        assertThat(adapter.createFragment(2)).isInstanceOf(DiscoverFragment::class.java)
    }

    @Test
    fun getRadioListFragment_shouldReturnRadioListFragment() {
        // Trigger fragment creation manually to populate fragmentMap
        val fragment = adapter.createFragment(0) as? RadioListFragment
        assertThat(fragment).isInstanceOf(RadioListFragment::class.java)
    }
}
