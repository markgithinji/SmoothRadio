package com.smoothradio.radio

import android.content.Intent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.viewpager2.widget.ViewPager2
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)

class MainActivityRobolectricTest {

    private lateinit var activity: MainActivity

    @Before
    fun setup() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
        activity = Robolectric.buildActivity(MainActivity::class.java, intent)
            .create()
            .resume()
            .get()
    }

    @Test
    fun getStationUsingSavedId_returnsFallbackIfNotFound() {
        val fallback = activity.getStationUsingSavedId()
        assertThat(fallback.id).isEqualTo(activity.radioListRecyclerViewAdapter.getStationAtPosition(0).id)
    }


 }
