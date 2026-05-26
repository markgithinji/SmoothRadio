package com.smoothradio.radio.core.domain.usecase

import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.domain.repository.FirebaseRepository
import com.smoothradio.radio.core.domain.repository.RadioRepository
import com.smoothradio.radio.core.logging.LoggingHelper
import com.smoothradio.radio.core.util.Resource
import com.smoothradio.radio.feature.radiolist.util.RadioStationsHelper
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case responsible for processing remote radio station links.
 *
 * This class observes a flow of remote stream links from [FirebaseRepository].
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
 * @property firebaseRepository Repository for fetching remote radio station links.
 */
@Singleton
class ProcessRemoteLinksUseCase @Inject constructor(
    private val radioRepository: RadioRepository,
    private val firebaseRepository: FirebaseRepository
) {

    suspend operator fun invoke() {
        firebaseRepository.getRemoteStreamLinksFlow().collect { resource ->
            when (resource) {
                is Resource.Success -> {
                    LoggingHelper.d("Received ${resource.data.size} links from Firestore", "ProcessRemoteLinks")

                    val localStations = radioRepository.allStations.first()
                    val newStations = RadioStationsHelper.createRadioStations(resource.data)

                    // Delete removed stations
                    val newIds = newStations.map { it.id }.toSet() // Get newly added stations
                    val toDelete =
                        localStations.filterNot { it.id in newIds } // Get stations in old list that are not in new list
                    if (toDelete.isNotEmpty()) {
                        LoggingHelper.d("Deleting ${toDelete.size} stations", "ProcessRemoteLinks")
                        radioRepository.deleteStations(toDelete)
                    }

                    val playingStationId = localStations.find { it.isPlaying }?.id
                        ?: newStations.firstOrNull()?.id // Get id of first station as playing station because none existed on first creation

                    val mergedStations = newStations.map { new ->
                        val existing =
                            localStations.find { it.id == new.id } // Find matching station in old list
                        new.copy( // Create new station with updated values to trigger diffUtil
                            isFavorite = existing?.isFavorite == true,
                            isPlaying = new.id == playingStationId
                        )
                    }

                    LoggingHelper.d("Inserting ${mergedStations.size} merged stations", "ProcessRemoteLinks")
                    radioRepository.insertStations(mergedStations)
                    LoggingHelper.d("Insert complete", "ProcessRemoteLinks")
                }

                is Resource.Error -> {
                    LoggingHelper.e("Error loading links: ${resource.message}", "ProcessRemoteLinks")
                }

                Resource.Loading -> {
                    LoggingHelper.d("Loading links from Firestore...", "ProcessRemoteLinks")
                }
            }
        }
    }
}
