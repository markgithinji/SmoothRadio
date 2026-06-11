package com.smoothradio.radio.core.ui.util

import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.R
import org.junit.Test

class LogoMapperTest {

    @Test
    fun getLogoById_knownIds_returnCorrectDrawables() {
        assertThat(LogoMapper.getLogoById(0)).isEqualTo(R.drawable.hopefm)
        assertThat(LogoMapper.getLogoById(1)).isEqualTo(R.drawable.soundcityradiologo)
        assertThat(LogoMapper.getLogoById(228)).isEqualTo(R.drawable.radio47logo)
    }

    @Test
    fun getLogoById_unknownId_returnsDefaultLogo() {
        assertThat(LogoMapper.getLogoById(-1)).isEqualTo(R.drawable.ic_radio_default)
        assertThat(LogoMapper.getLogoById(9999)).isEqualTo(R.drawable.ic_radio_default)
    }
}
