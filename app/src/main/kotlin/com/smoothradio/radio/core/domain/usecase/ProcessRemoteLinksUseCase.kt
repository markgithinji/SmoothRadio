package com.smoothradio.radio.core.domain.usecase

import android.util.Log
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.domain.repository.RadioLinkRepository
import com.smoothradio.radio.core.domain.repository.RadioRepository
import com.smoothradio.radio.core.util.Resource
import com.smoothradio.radio.feature.radio_list.util.RadioStationsHelper
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton


/**
 * Use case responsible for processing remote radio station links.
 *
 * This class observes a flow of remote stream links from [RadioLinkRepository].
 * When new links are successfully fetched, it:
 * 1. Creates [RadioStation] objects from the remote data.
 * 2. If no local stations exist, it inserts the new stations and sets the first one as playing.
 * 3. If local stations exist, it preserves the favorite status and currently playing station
 *    from the local data and applies them to the corresponding new stations.
 * 4. Inserts the updated list of new stations into the local [RadioRepository].
 *
 * It also includes placeholders for handling error and loading states from the remote data source.
 *
 * @property radioRepository Repository for managing local radio station data.
 * @property radioLinkRepository Repository for fetching remote radio station links.
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
                    val localStations = radioRepository.allStations.first()
                    val newStations = RadioStationsHelper.createRadioStations(resource.data)

                    if (localStations.isEmpty()) {
                        radioRepository.insertStations(newStations)
                        radioRepository.setPlayingStation(newStations.first().id)
                        return@collect
                    }

                    val favorites = localStations.filter { it.isFavorite }
                    val playingStationId = localStations.find { it.isPlaying }?.id

                    newStations.forEach { station ->
                        station.isFavorite = station in favorites
                        station.isPlaying = station.id == playingStationId
                    }

                    radioRepository.insertStations(newStations)
                }

                is Resource.Error -> {
                    // TODO: Handle error state (e.g. log or notify)
                }

                Resource.Loading -> {
                    // TODO: Handle loading state (e.g. show loading UI)
                }

            }
        }
    }
}
