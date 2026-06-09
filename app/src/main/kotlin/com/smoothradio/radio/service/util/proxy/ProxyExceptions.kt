package com.smoothradio.radio.service.util.proxy

import java.io.IOException

/**
 * Thrown when the requested stream position has already been purged from the proxy's rolling buffer.
 */
class BufferEvictedException(
    val evictedPositionMs: Long,
    val newValidPositionBytes: Long  // This is in bytes, not milliseconds
) : IOException("Buffer evicted at position ${evictedPositionMs}ms, new start byte offset: $newValidPositionBytes")

/**
 * Thrown when the station is currently unreachable or down.
 */
class StationUnreachableException(url: String?) : IOException("Station at $url is currently unreachable")

/**
 * Thrown when the stream connection was established but no audio data was received.
 */
class EmptyStreamException : IOException("Connected to station but no audio data received")

/**
 * Thrown when the proxy encounters a critical local storage error.
 */
class ProxyCacheException(message: String) : IOException(message)
