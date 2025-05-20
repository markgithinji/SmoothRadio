package com.smoothradio.radio.feature.radio_list.usecase

import com.smoothradio.radio.core.domain.repository.RadioRepository
import com.smoothradio.radio.core.util.Resource
import com.smoothradio.radio.core.domain.usecase.ProcessRemoteLinksUseCase
import com.smoothradio.radio.feature.radio_list.util.RadioStationsHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultProcessRemoteLinksUseCase @Inject constructor(
    private val repository: RadioRepository
) : ProcessRemoteLinksUseCase {

    override suspend fun invoke(resource: Resource<List<String>>) {
        when (resource) {
            is Resource.Success -> {
                val local = repository.allStations.first()
                val newStations = RadioStationsHelper.createRadioStations(resource.data, local)

                repository.insertStations(newStations)

                val playing = repository.playingStation.firstOrNull()
                if (playing == null && newStations.isNotEmpty()) {
                    repository.setPlayingStation(newStations.first().id)
                }
            }

            is Resource.Error -> {
                // You can optionally expose an error handler via callback or log here
            }

            Resource.Loading -> {
                // Optional: no-op
            }
        }
    }
}
