package com.smoothradio.radio.service.util.command

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServiceCommandMapperTest {

    private lateinit var mapper: ServiceCommandMapper

    @Before
    fun setup() {
        mapper = ServiceCommandMapper()
    }

    @Test
    fun `map START action should return Start command`() {
        val intent = Intent(ServiceCommand.ACTION_START).apply {
            putExtra(ServiceCommand.EXTRA_LINK, "http://test.com")
            putExtra(ServiceCommand.EXTRA_STATION_NAME, "Test Station")
            putExtra(ServiceCommand.EXTRA_LOGO, 123)
        }

        val command = mapper.map(intent)

        assertThat(command).isInstanceOf(ServiceCommand.Start::class.java)
        val startCommand = command as ServiceCommand.Start
        assertThat(startCommand.link).isEqualTo("http://test.com")
        assertThat(startCommand.name).isEqualTo("Test Station")
        assertThat(startCommand.logo).isEqualTo(123)
    }

    @Test
    fun `map SHOW_AD action should return ShowAd command`() {
        val intent = Intent(ServiceCommand.ACTION_SHOW_AD).apply {
            putExtra(ServiceCommand.EXTRA_LINK, "http://ad.com")
            putExtra(ServiceCommand.EXTRA_STATION_NAME, "Ad Station")
            putExtra(ServiceCommand.EXTRA_LOGO, 456)
        }

        val command = mapper.map(intent)

        assertThat(command).isInstanceOf(ServiceCommand.ShowAd::class.java)
        val showAdCommand = command as ServiceCommand.ShowAd
        assertThat(showAdCommand.link).isEqualTo("http://ad.com")
        assertThat(showAdCommand.name).isEqualTo("Ad Station")
        assertThat(showAdCommand.logo).isEqualTo(456)
    }

    @Test
    fun `map SEEK_TO action should return SeekTo command`() {
        val intent = Intent(ServiceCommand.ACTION_SEEK_TO).apply {
            putExtra(ServiceCommand.EXTRA_POSITION, 5000L)
        }

        val command = mapper.map(intent)

        assertThat(command).isInstanceOf(ServiceCommand.SeekTo::class.java)
        assertThat((command as ServiceCommand.SeekTo).position).isEqualTo(5000L)
    }

    @Test
    fun `map unknown action should return None`() {
        val intent = Intent("UNKNOWN_ACTION")
        val command = mapper.map(intent)
        assertThat(command).isEqualTo(ServiceCommand.None)
    }

    @Test
    fun `map null action should return None`() {
        val intent = Intent()
        val command = mapper.map(intent)
        assertThat(command).isEqualTo(ServiceCommand.None)
    }

    @Test
    fun `map STOP action should return Stop command`() {
        val intent = Intent(ServiceCommand.ACTION_STOP)
        val command = mapper.map(intent)
        assertThat(command).isEqualTo(ServiceCommand.Stop)
    }
}
