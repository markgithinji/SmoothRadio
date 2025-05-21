package com.smoothradio.radio.feature.radio_list.usecase

import com.smoothradio.radio.core.domain.repository.RadioLinkRepository
import com.smoothradio.radio.core.domain.repository.RadioRepository
import com.smoothradio.radio.core.domain.usecase.ProcessRemoteLinksUseCase
import com.smoothradio.radio.core.util.Resource
import com.smoothradio.radio.feature.radio_list.util.RadioStationsHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A use case responsible for processing remote radio station links.
 *
 * This class observes a flow of radio stream links from `radioLinkRepository`.
 * When new remote links are successfully fetched:
 *  - It retrieves the existing local radio stations from `radioRepository`.
 *  - It uses `RadioStationsHelper` to create new `RadioStation` objects based on the remote data,
 *    potentially updating with existing local stations.
 *  - It inserts the newly created or updated stations into the `radioRepository`.
 *  - If no station is currently playing and new stations were added, it sets the first of the
 *    new stations as the currently playing station in `radioRepository`.
 *
 * This use case handles different states of the resource fetching process:
 *  - `Resource.Success`: Processes the data as described above.
 *  - `Resource.Error`:  Currently, errors are not explicitly handled but can be extended to include
 *                       logging or analytics.
 *  - `Resource.Loading`: Currently, the loading state is not explicitly tracked but can be extended
 *                        if needed.
 *
 * This class is annotated with `@Singleton` to ensure only one instance is created and used
 * throughout the application. It uses constructor injection for its dependencies.
 *
 * @property radioRepository Repository for managing local radio station data.
 * @property radioLinkRepository Repository for fetching remote radio stream links.
 */
@Singleton
class DefaultProcessRemoteLinksUseCase @Inject constructor(
    private val radioRepository: RadioRepository,
    private val radioLinkRepository: RadioLinkRepository
) : ProcessRemoteLinksUseCase {

    override suspend fun invoke() {
        radioLinkRepository.getRemoteStreamLinksFlow().collect { resource ->
            when (resource) {
                is Resource.Success -> {
                    val local = radioRepository.allStations.first()
                    val newStations = RadioStationsHelper.createRadioStations(resource.data, local)

                    radioRepository.insertStations(newStations)

                    // If no station is currently playing and new stations were added, it sets the first of the
                    // new stations as the currently playing station
                    val playing = radioRepository.playingStation.firstOrNull()
                    if (playing == null && newStations.isNotEmpty()) {
                        radioRepository.setPlayingStation(newStations.first().id)
                    }
                }

                is Resource.Error -> {
                    // Optionally handle error (log, analytics, etc.)
                }

                Resource.Loading -> {
                    // Optional: track loading state
                }
            }
        }
    }
}
