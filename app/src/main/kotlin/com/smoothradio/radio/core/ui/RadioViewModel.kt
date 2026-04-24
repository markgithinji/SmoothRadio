package com.smoothradio.radio.core.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smoothradio.radio.core.domain.model.RadioStation
import com.smoothradio.radio.core.domain.repository.RadioLinkRepository
import com.smoothradio.radio.core.domain.repository.RadioRepository
import com.smoothradio.radio.core.domain.repository.ViewPreferenceRepository
import com.smoothradio.radio.core.domain.usecase.ProcessRemoteLinksUseCase
import com.smoothradio.radio.core.domain.usecase.ToggleFavoriteUseCase
import com.smoothradio.radio.core.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RadioViewModel @Inject constructor(
    application: Application,
    private val radioLinkRepository: RadioLinkRepository,
    private val radioRepository: RadioRepository,
    private val processRemoteLinksUseCase: ProcessRemoteLinksUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val viewPreferenceRepository: ViewPreferenceRepository
) : AndroidViewModel(application) {

    val allStations = radioRepository.allStations
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val favoriteStations = radioRepository.favoriteStations
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _favoriteLimitExceeded = MutableSharedFlow<String>()
    val favoriteLimitExceeded: SharedFlow<String> = _favoriteLimitExceeded

    private val _isGridView = MutableStateFlow(false)
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    // Filtered stations based on search query
    val filteredStations = combine(
        allStations,
        _searchQuery
    ) { stations, query ->
        if (query.isEmpty()) {
            stations
        } else {
            stations.filter { station ->
                station.stationName.contains(query, ignoreCase = true) ||
                        station.frequency.contains(query, ignoreCase = true) ||
                        station.location.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        loadViewPreference()
    }

    private fun loadViewPreference() {
        viewModelScope.launch {
            viewPreferenceRepository.getIsGridViewFlow().collect { isGridView ->
                _isGridView.value = isGridView
            }
        }
    }

    fun toggleViewPreference() {
        viewModelScope.launch {
            val newValue = !_isGridView.value
            viewPreferenceRepository.saveIsGridView(newValue)
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSearchActive(active: Boolean) {
        _isSearchActive.value = active
        if (!active) {
            _searchQuery.value = ""
        }
    }

    fun observeAndProcessRemoteLinks() {
        viewModelScope.launch {
            processRemoteLinksUseCase()
        }
    }

    fun insertStations(stations: List<RadioStation>) {
        viewModelScope.launch {
            radioRepository.insertStations(stations)
        }
    }

    fun toggleFavorite(stationId: Int, newState: Boolean) {
        viewModelScope.launch {
            when (val result = toggleFavoriteUseCase(stationId, newState)) {
                is Resource.Error -> {
                    result.message?.let { message ->
                        _favoriteLimitExceeded.emit(message)
                    }
                }

                is Resource.Success -> {
                    // Successfully toggled favorite, no action needed
                }

                is Resource.Loading -> {
                    // Not applicable for this use case
                }
            }
        }
    }

    fun setCurrentPage(page: Int) {
        _currentPage.value = page
    }

    override fun onCleared() {
        super.onCleared()
        radioLinkRepository.clear()
    }
}
