package com.smoothradio.radio.core.util

import android.content.Context
import android.content.ContextWrapper
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
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        cacheDir = File(baseContext.filesDir, "fake_cache").apply {
            mkdirs()
            File(this, "dummy.txt").writeText("dummy")
        }

        externalCacheDir = File(baseContext.filesDir, "fake_external_cache").apply {
            mkdirs()
            File(this, "dummy_ext.txt").writeText("external dummy")
        }

        context = TestContext(baseContext, cacheDir, externalCacheDir)
    }

    @Test
    fun clearAppCache_shouldDeleteAllCacheContents() {
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

    private class TestContext(
        base: Context,
        private val internal: File,
        private val external: File?
    ) : ContextWrapper(base) {
        override fun getCacheDir(): File = internal
        override fun getExternalCacheDir(): File? = external
    }
}

