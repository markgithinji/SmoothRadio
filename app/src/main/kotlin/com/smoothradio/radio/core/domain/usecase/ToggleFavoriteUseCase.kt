package com.smoothradio.radio.core.domain.usecase

import com.smoothradio.radio.core.domain.repository.RadioRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ToggleFavoriteUseCase @Inject constructor(
    private val radioRepository: RadioRepository
) {
    suspend operator fun invoke(stationId: Int, newState: Boolean): Boolean {
        val currentFavorites = radioRepository.favoriteStations.first()

        if (newState && currentFavorites.size >= MAX_FAVORITES) return false

        radioRepository.updateFavoriteStatus(stationId, newState)
        return true
    }

    companion object {
        private const val MAX_FAVORITES = 20
    }
}
