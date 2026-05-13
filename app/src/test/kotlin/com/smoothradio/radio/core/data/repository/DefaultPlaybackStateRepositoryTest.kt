package com.smoothradio.radio.core.data.repository

import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.core.domain.model.StreamStates
import com.smoothradio.radio.core.domain.repository.PlaybackStateRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@ExperimentalCoroutinesApi
@RunWith(JUnit4::class)
class DefaultPlaybackStateRepositoryTest {

    private lateinit var repository: PlaybackStateRepository

    @Before
    fun setup() {
        repository = DefaultPlaybackStateRepository()
    }

    @Test
    fun playbackState_initialValueShouldBeIdle() {
        assertThat(repository.playbackState.value).isEqualTo(StreamStates.IDLE)
    }

    @Test
    fun updateState_shouldUpdatePlaybackState() {
        repository.updateState(StreamStates.PLAYING)
        assertThat(repository.playbackState.value).isEqualTo(StreamStates.PLAYING)

        repository.updateState(StreamStates.BUFFERING)
        assertThat(repository.playbackState.value).isEqualTo(StreamStates.BUFFERING)
    }

    @Test
    fun metadata_initialValueShouldBeEmpty() {
        assertThat(repository.metadata.value).isEmpty()
    }

    @Test
    fun updateMetadata_shouldUpdateMetadata() {
        val testMetadata = "Artist - Song Title"
        repository.updateMetadata(testMetadata)
        assertThat(repository.metadata.value).isEqualTo(testMetadata)
    }

    @Test
    fun stationName_initialValueShouldBeNull() {
        assertThat(repository.stationName.value).isNull()
    }

    @Test
    fun updateStationName_shouldUpdateStationName() {
        val testStation = "Cool Radio"
        repository.updateStationName(testStation)
        assertThat(repository.stationName.value).isEqualTo(testStation)

        repository.updateStationName(null)
        assertThat(repository.stationName.value).isNull()
    }
}
