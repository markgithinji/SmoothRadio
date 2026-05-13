package com.smoothradio.radio.core.domain.model

sealed class ToastType {
    data class Error(val message: String) : ToastType()
    data class Success(val message: String) : ToastType()
    data class Warning(val message: String) : ToastType()
    data class Info(val message: String) : ToastType()
}