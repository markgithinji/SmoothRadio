package com.smoothradio.radio.feature.info.domain.model

enum class IssueCategory(val displayName: String) {
    AUDIO_ISSUES("Audio Issues"),
    APP_CRASHING("App Crashing"),
    STREAM_LOADING_ERROR("Stream Loading Error"),
    MISSING_STATION("Missing Station"),
    OTHER("Other")
}
