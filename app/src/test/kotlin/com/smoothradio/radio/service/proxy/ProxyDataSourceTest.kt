@file:OptIn(UnstableApi::class)

package com.smoothradio.radio.service.proxy

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.util.PlaybackConstants
import com.smoothradio.radio.service.util.proxy.AudioProxy
import com.smoothradio.radio.service.util.proxy.BufferEvictedException
import com.smoothradio.radio.service.util.proxy.EmptyStreamException
import com.smoothradio.radio.service.util.proxy.ProxyCacheException
import com.smoothradio.radio.service.util.proxy.ProxyDataSource
import com.smoothradio.radio.service.util.proxy.StationUnreachableException
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProxyDataSourceTest {

    private lateinit var proxy: AudioProxy
    private lateinit var dataSource: ProxyDataSource

    @Before
    fun setup() {
        proxy = mock()
        dataSource = ProxyDataSource(proxy)
    }

    @Test
    fun `open should capture session tag and return unset length`() {
        whenever(proxy.sessionTag).doReturn("test-tag")
        
        val dataSpec = DataSpec(Uri.parse("proxy://smoothradio/stream"))
        val length = dataSource.open(dataSpec)

        assertThat(length).isEqualTo(-1L) // C.LENGTH_UNSET
        verify(proxy).sessionTag
    }

    @Test
    fun `read should call proxy readData with correct parameters`() {
        whenever(proxy.sessionTag).doReturn("tag1")
        dataSource.open(DataSpec(Uri.parse("proxy://smoothradio/stream")))

        val buffer = ByteArray(1024)
        whenever(proxy.readData(any(), any(), any(), any(), any())).doReturn(512)

        val read = dataSource.read(buffer, 0, 1024)

        assertThat(read).isEqualTo(512)
        verify(proxy).readData(org.mockito.kotlin.eq("tag1"), org.mockito.kotlin.eq(0L), any(), any(), any())
    }

    @Test(expected = BufferEvictedException::class)
    fun `read should throw BufferEvictedException when proxy returns -2`() {
        whenever(proxy.sessionTag).doReturn("tag1")
        whenever(proxy.totalBytesDropped).doReturn(5000L)
        dataSource.open(DataSpec(Uri.parse("proxy://smoothradio/stream")))

        whenever(proxy.readData(any(), any(), any(), any(), any())).doReturn(-2)

        dataSource.read(ByteArray(100), 0, 100)
    }

    @Test
    fun `read should return EOF if session tag changes`() {
        whenever(proxy.sessionTag).doReturn("tag1")
        dataSource.open(DataSpec(Uri.parse("proxy://smoothradio/stream")))

        // Change tag in proxy
        whenever(proxy.sessionTag).doReturn("tag2")

        val read = dataSource.read(ByteArray(100), 0, 100)
        assertThat(read).isEqualTo(-1) // C.RESULT_END_OF_INPUT
    }

    @Test(expected = StationUnreachableException::class)
    fun `read should throw StationUnreachableException when proxy has ERROR_UNREACHABLE`() {
        whenever(proxy.sessionTag).doReturn("tag1")
        whenever(proxy.terminalError).doReturn(PlaybackConstants.ERROR_UNREACHABLE)
        dataSource.open(DataSpec(Uri.parse("proxy://smoothradio/stream")))

        whenever(proxy.readData(org.mockito.kotlin.eq("tag1"), any(), any(), any(), any())).doReturn(-3)

        dataSource.read(ByteArray(100), 0, 100)
    }

    @Test(expected = EmptyStreamException::class)
    fun `read should throw EmptyStreamException when proxy has ERROR_EMPTY_STREAM`() {
        whenever(proxy.sessionTag).doReturn("tag1")
        whenever(proxy.terminalError).doReturn(PlaybackConstants.ERROR_EMPTY_STREAM)
        dataSource.open(DataSpec(Uri.parse("proxy://smoothradio/stream")))

        whenever(proxy.readData(org.mockito.kotlin.eq("tag1"), any(), any(), any(), any())).doReturn(-3)

        dataSource.read(ByteArray(100), 0, 100)
    }

    @Test(expected = ProxyCacheException::class)
    fun `read should throw ProxyCacheException when proxy has ERROR_CACHE_ERROR`() {
        whenever(proxy.sessionTag).doReturn("tag1")
        whenever(proxy.terminalError).doReturn(PlaybackConstants.ERROR_CACHE_ERROR)
        dataSource.open(DataSpec(Uri.parse("proxy://smoothradio/stream")))

        whenever(proxy.readData(org.mockito.kotlin.eq("tag1"), any(), any(), any(), any())).doReturn(-3)

        dataSource.read(ByteArray(100), 0, 100)
    }
}
