package com.smoothradio.radio.feature.radiolist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smoothradio.radio.core.domain.repository.FirebaseRepository
import com.smoothradio.radio.core.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReportIssueViewModel @Inject constructor(
    private val firebaseRepository: FirebaseRepository
) : ViewModel() {

    private val _submissionState = MutableStateFlow<Resource<Unit>?>(null)
    val submissionState = _submissionState.asStateFlow()

    fun submitReport(
        category: String,
        description: String,
        appVersion: String,
        deviceInfo: String,
        androidVersion: String
    ) {
        viewModelScope.launch {
            _submissionState.value = Resource.Loading
            _submissionState.value = firebaseRepository.submitReport(
                category = category,
                description = description,
                appVersion = appVersion,
                deviceInfo = deviceInfo,
                androidVersion = androidVersion
            )
        }
    }

    fun resetState() {
        _submissionState.value = null
    }
}
