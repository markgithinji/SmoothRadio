package com.smoothradio.radio.core.domain.usecase

import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.domain.repository.RadioLinkRepository
import com.smoothradio.radio.core.domain.repository.RadioRepository
import com.smoothradio.radio.core.util.Resource
import com.smoothradio.radio.feature.radio_list.util.RadioStationsHelper
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton


/**
 * A use case responsible for processing remote radio station links.
 *
 * This class fetches remote radio station links, converts them into [RadioStation] objects,
 * and updates the local radio station data. It handles scenarios where local data is empty
 * or needs to be merged with new remote data, preserving user favorites and the currently
 * playing station.
 *
 * It observes a flow of remote stream links from the [radioLinkRepository].
 * On successful retrieval:
 *  - It fetches the current local stations from [radioRepository].
 *  - It converts the remote links into a list of [RadioStation] objects using [RadioStationsHelper].
 *  - If no local stations exist, it inserts the new stations and sets the first new station as playing.
 *  - If local stations exist, it merges the new stations with the local data, ensuring that:
 *      - Favorite statuses from local stations are applied to matching new stations.
 *      - The currently playing station status is maintained.
 *
 * Error and loading states from the remote link fetching process can be optionally handled.
 */
@Singleton
class ProcessRemoteLinksUseCase @Inject constructor(
    private val radioRepository: RadioRepository,
    private val radioLinkRepository: RadioLinkRepository
) {

    suspend operator fun invoke() {
        radioLinkRepository.getRemoteStreamLinksFlow().collect { resource ->
            when (resource) {
                is Resource.Success -> {
                    val local = radioRepository.allStations.first()
                    val newStations =
                        RadioStationsHelper.createRadioStations(resource.data)

                    if (local.isEmpty()) {
                        radioRepository.insertStations(newStations)
                        // Since no station is currently playing and new stations were added, it sets the first of the
                        // new stations as the currently playing station
                        radioRepository.setPlayingStation(newStations.first().id)
                        return@collect
                    }

                    // Merge local favorites and playing states
                    val favouriteStations = mutableListOf<RadioStation>()
                    var playingStationId = 0

                    local.forEach {
                        if (it.isPlaying) playingStationId = it.id
                        if (it.isFavorite) favouriteStations.add(it)
                    }

                    newStations.forEach {
                        it.isFavorite = favouriteStations.contains(it)
                        it.isPlaying = it.id == playingStationId
                    }

                    radioRepository.insertStations(newStations)
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
