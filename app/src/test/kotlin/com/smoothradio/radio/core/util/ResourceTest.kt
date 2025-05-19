package com.smoothradio.radio.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ResourceTest {

    @Test
    fun success_shouldCreateSuccessResourceWithData() {
        val data = "Test Data"
        val resource = Resource.Success(data)

        assertThat(resource).isInstanceOf(Resource.Success::class.java)
        assertThat((resource as Resource.Success).data).isEqualTo(data)
    }

    @Test
    fun error_shouldCreateErrorResourceWithMessage() {
        val errorMessage = "An error occurred"
        val resource = Resource.Error(errorMessage)

        assertThat(resource).isInstanceOf(Resource.Error::class.java)
        assertThat((resource as Resource.Error).message).isEqualTo(errorMessage)
    }

    @Test
    fun loading_shouldCreateLoadingResource() {
        val resource = Resource.Loading

        assertThat(resource).isInstanceOf(Resource.Loading::class.java)
    }
}
