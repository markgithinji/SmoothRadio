package com.smoothradio.radio.feature.radio_list.usecase

import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.domain.repository.RadioRepository
import com.smoothradio.radio.core.domain.usecase.ProcessRemoteLinksUseCase
import com.smoothradio.radio.core.util.Resource
import com.smoothradio.radio.feature.radio_list.util.RadioStationsHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeProcessRemoteLinksUseCase @Inject constructor(
    private val radioRepository: RadioRepository
) : ProcessRemoteLinksUseCase {

    val lastInvokedWith = MutableStateFlow<Resource<List<String>>?>(null)

    override suspend fun invoke(resource: Resource<List<String>>) {
        lastInvokedWith.value = resource

        if (resource is Resource.Success) {
            val localStations = radioRepository.allStations.first()
            val newStations: List<RadioStation> =
                RadioStationsHelper.createRadioStations(resource.data, localStations)

            radioRepository.insertStations(newStations)

            if (radioRepository.playingStation.first() == null && newStations.isNotEmpty()) {
                radioRepository.setPlayingStation(newStations.first().id)
            }
        }
    }
}
