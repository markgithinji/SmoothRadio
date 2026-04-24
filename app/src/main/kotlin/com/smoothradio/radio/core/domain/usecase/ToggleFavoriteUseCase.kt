package com.smoothradio.radio.core.domain.usecase

import com.smoothradio.radio.core.domain.repository.RadioRepository
import com.smoothradio.radio.core.util.Resource
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ToggleFavoriteUseCase @Inject constructor(
    private val radioRepository: RadioRepository
) {
    suspend operator fun invoke(stationId: Int, newState: Boolean): Resource<Unit> {
        val currentFavorites = radioRepository.favoriteStations.first()

        if (newState && currentFavorites.size >= MAX_FAVORITES) {
            return Resource.Error("You can only have $MAX_FAVORITES favorite stations")
        }

        radioRepository.updateFavoriteStatus(stationId, newState)
        return Resource.Success(Unit)
    }

    companion object {
        private const val MAX_FAVORITES = 20
    }
}