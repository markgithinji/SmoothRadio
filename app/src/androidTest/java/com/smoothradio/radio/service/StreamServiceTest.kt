package com.smoothradio.radio.service

import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import com.google.common.truth.Truth.assertThat
import com.smoothradio.radio.R
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class StreamServiceTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    private lateinit var context: Context
    private var latch: CountDownLatch = CountDownLatch(1)
    private val receivedStates = mutableListOf<String>()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        receivedStates.clear()
        latch = CountDownLatch(1) // re-init if reused

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val state = intent?.getStringExtra(StreamService.EXTRA_STATE)
                if (state != null) {
                    receivedStates.add(state)
                    latch.countDown()
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(StreamService.ACTION_EVENT_CHANGE))
    }

    @Test
    fun onStartCommand_ReturnValue_VerifyCorrectReturnValue() {
        val service = StreamService()
        val result = service.onStartCommand(null, 0, 0)
        assertThat(result).isEqualTo(Service.START_NOT_STICKY)
    }

    @Test
    fun startService_shouldBroadcastPreparingState() {
        val startIntent = Intent(context, StreamService::class.java).apply {
            action = StreamService.ACTION_START
            putExtra(StreamService.EXTRA_LINK, "https://a5.asurahosting.com:7530/radio.mp3")
            putExtra(StreamService.EXTRA_LOGO, R.drawable.hopefm)
            putExtra(StreamService.EXTRA_STATION_NAME, "Hope FM")
        }

        context.startService(startIntent)


        // Wait until broadcast received or timeout
        val success = latch.await(5, TimeUnit.SECONDS)

        assertThat(success).isTrue() // Ensure a broadcast was received
        assertThat(receivedStates).contains(StreamService.StreamStates.PLAYING)
    }


    @Test
    fun sendBroadcast_actionGetState_shouldRespondWithCurrentState() {
        // Start service
        val startIntent = Intent(context, StreamService::class.java).apply {
            action = StreamService.ACTION_START
            putExtra(StreamService.EXTRA_LINK, "https://example.com")
            putExtra(StreamService.EXTRA_LOGO, R.drawable.hopefm)
            putExtra(StreamService.EXTRA_STATION_NAME, "Hope FM")
        }
        context.startService(startIntent)

        Thread.sleep(800) // Let service stabilize

        // Trigger get-state
        val getStateIntent = Intent(StreamService.ACTION_GET_STATE).setPackage(context.packageName)
        context.sendBroadcast(getStateIntent)

        val success = latch.await(5, TimeUnit.SECONDS)
        assertThat(success).isTrue()
        assertThat(receivedStates).isNotEmpty()
    }

}
