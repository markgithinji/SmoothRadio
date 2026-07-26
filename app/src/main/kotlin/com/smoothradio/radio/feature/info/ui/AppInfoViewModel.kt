package com.smoothradio.radio.feature.info.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smoothradio.radio.BuildConfig
import com.smoothradio.radio.core.domain.repository.FirebaseRepository
import com.smoothradio.radio.core.domain.repository.ViewPreferenceRepository
import com.smoothradio.radio.core.util.Resource
import com.smoothradio.radio.feature.info.domain.usecase.GetChangelogItemsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppInfoViewModel @Inject constructor(
    private val viewPreferenceRepository: ViewPreferenceRepository,
    private val firebaseRepository: FirebaseRepository,
    getChangelogItemsUseCase: GetChangelogItemsUseCase
) : ViewModel() {

    private val _shouldShowChangelog = MutableStateFlow(false)
    val shouldShowChangelog: StateFlow<Boolean> = _shouldShowChangelog.asStateFlow()

    private val _reportState = MutableStateFlow<Resource<Unit>?>(null)
    val reportState = _reportState.asStateFlow()

    val changelogItems = getChangelogItemsUseCase()

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

    fun submitReport(
        category: String,
        description: String,
        appVersion: String,
        deviceInfo: String,
        androidVersion: String
    ) {
        viewModelScope.launch {
            _reportState.value = Resource.Loading
            _reportState.value = firebaseRepository.submitReport(
                category = category,
                description = description,
                appVersion = appVersion,
                deviceInfo = deviceInfo,
                androidVersion = androidVersion
            )
        }
    }

    fun resetReportState() {
        _reportState.value = null
    }
}
