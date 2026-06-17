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

    val changelogItems = listOf(
        ChangelogItem(
            "Modern UI Redesign",
            "A fresh look and feel with Material 3 styling and updated icons."
        ),
        ChangelogItem(
            "Interactive Seek Bar",
            "Take full control of your listening with the new interactive seek bar."
        ),
        ChangelogItem(
            "Faster Playback",
            "Start listening instantly with optimized stream loading."
        ),
        ChangelogItem(
            "Fewer Interruptions",
            "We've reduced ad frequency for a smoother listening experience."
        ),
        ChangelogItem(
            "Dark & Light Modes",
            "Beautifully designed for both environments, supporting your system's theme perfectly."
        ),
        ChangelogItem(
            "Adaptive Design",
            "Seamlessly optimized for all screen sizes, from compact phones to large tablets."
        ),
        ChangelogItem(
            "Built-in Equalizer",
            "Fine-tune your audio with the new integrated equalizer."
        ),
        ChangelogItem(
            "Google Cast Support",
            "Easily cast your favorite stations to your TV or speakers."
        ),
        ChangelogItem(
            "Grid & List Views",
            "Choose how you browse with customizable station layouts."
        ),
        ChangelogItem("Smarter Search", "Find your favorite stations faster than ever before.")
    )

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

data class ChangelogItem(val title: String, val description: String)
