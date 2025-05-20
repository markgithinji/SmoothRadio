package com.smoothradio.radio.core.domain.usecase

import com.smoothradio.radio.core.util.Resource

interface ProcessRemoteLinksUseCase {
    suspend operator fun invoke(resource: Resource<List<String>>)
}
