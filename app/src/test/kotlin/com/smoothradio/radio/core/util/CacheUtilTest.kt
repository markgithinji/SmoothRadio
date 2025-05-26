package com.smoothradio.radio.core.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class CacheUtilTest {

    private lateinit var cacheDir: File
    private lateinit var externalCacheDir: File
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        cacheDir = context.cacheDir
        externalCacheDir = context.externalCacheDir!!
    }

    @Test
    fun clearAppCache_shouldDeleteAllCacheContents() {
        val filename = "test.txt"

        File(context.cacheDir, filename).createNewFile()
        File(context.externalCacheDir, filename).createNewFile()

        assertThat(cacheDir.exists()).isTrue()
        assertThat(cacheDir.listFiles()).isNotEmpty()

        assertThat(externalCacheDir.exists()).isTrue()
        assertThat(externalCacheDir.listFiles()).isNotEmpty()

        CacheUtil.clearAppCache(context)

        assertThat(cacheDir.exists()).isTrue()
        assertThat(cacheDir.listFiles()).isEmpty()

        assertThat(externalCacheDir.exists()).isTrue()
        assertThat(externalCacheDir.listFiles()).isEmpty()
    }
}

