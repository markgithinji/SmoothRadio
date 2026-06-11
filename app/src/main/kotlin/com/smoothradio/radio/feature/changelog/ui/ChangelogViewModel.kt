package com.smoothradio.radio.feature.changelog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smoothradio.radio.BuildConfig
import com.smoothradio.radio.core.domain.repository.ViewPreferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChangelogViewModel @Inject constructor(
    private val viewPreferenceRepository: ViewPreferenceRepository
) : ViewModel() {

    private val _shouldShowChangelog = MutableStateFlow(false)
    val shouldShowChangelog: StateFlow<Boolean> = _shouldShowChangelog.asStateFlow()

    init {
        checkVersion()
    }

    private fun checkVersion() {
        viewModelScope.launch {
            val lastVersion = viewPreferenceRepository.getLastShownVersion()
            val currentVersion = BuildConfig.VERSION_CODE
            if (currentVersion > lastVersion) {
                delay(2000) // 2-second delay for a smoother entry
                _shouldShowChangelog.value = true
            }
        }
    }

    fun onChangelogDismissed() {
        viewModelScope.launch {
            viewPreferenceRepository.saveLastShownVersion(BuildConfig.VERSION_CODE)
            _shouldShowChangelog.value = false
        }
    }

    fun showChangelog() {
        _shouldShowChangelog.value = true
    }
}
