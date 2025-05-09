package com.smoothradio.radio.core.util

data class Resource<T>(
    val status: Status,
    val data: T? = null,
    val message: String? = null
) {
    enum class Status { SUCCESS, ERROR, LOADING }

    companion object {
        fun <T> success(data: T): Resource<T> = Resource(Status.SUCCESS, data)
        fun <T> error(message: String): Resource<T> = Resource(Status.ERROR, null, message)
        fun <T> loading(): Resource<T> = Resource(Status.LOADING)
    }
}
