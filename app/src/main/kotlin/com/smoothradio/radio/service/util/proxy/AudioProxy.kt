package com.smoothradio.radio.service.util.proxy

import kotlinx.coroutines.flow.StateFlow

interface AudioProxy {
    val sessionTag: String
    val proxyState: StateFlow<ProxyState>
    val terminalError: Int
    val estimatedBytesPerMs: Double
    val totalBytesDropped: Long
    val totalBytesWritten: Long
    val totalBytesReceived: Long
    val lastReadPosition: Long

    fun start(streamUrl: String)
    fun stop()
    fun isStartedFor(url: String): Boolean
    fun updateLastReadPosition(pos: Long)
    fun getMetadataForOffset(offset: Long): String?
    fun readData(tag: String, position: Long, buffer: ByteArray, offset: Int, length: Int): Int
    fun updateBitrateEstimation()
}
