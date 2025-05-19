package com.smoothradio.radio.feature.player.util

import android.content.Intent
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.smoothradio.radio.R
import com.smoothradio.radio.service.StreamService
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class TimerSetterHelper(
    private val fragmentActivity: FragmentActivity,
    private val coordinatorLayout: LinearLayout
) {
    fun showTimerPicker() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val time1 =
            android.text.format.DateFormat.getTimeFormat(fragmentActivity).format(calendar.time)

        MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(hour)
            .setMinute(minute)
            .setTitleText(fragmentActivity.getString(R.string.set_timer_title))
            .build().apply {
                show(fragmentActivity.supportFragmentManager, "SetTimerFrag")
                addOnPositiveButtonClickListener { handleTimerSet(this, time1) }
            }
    }

    private fun handleTimerSet(picker: MaterialTimePicker, time1: String) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, picker.hour)
            set(Calendar.MINUTE, picker.minute)
            set(Calendar.SECOND, 0)
        }

        val timeInMillis = calendar.timeInMillis
        val setTimerIntent = Intent().apply {
            putExtra(StreamService.EXTRA_TIME_IN_MILLIS, timeInMillis)
            action = StreamService.ACTION_SET_TIMER
            setPackage(fragmentActivity.packageName)
        }
        fragmentActivity.sendBroadcast(setTimerIntent)

        val time2 =
            android.text.format.DateFormat.getTimeFormat(fragmentActivity).format(calendar.time)

        showTimeDifference(time1, time2)
    }

    private fun showTimeDifference(time1: String, time2: String) {
        try {
            val sdf = SimpleDateFormat("hh:mm:ss aa", Locale.getDefault())
            val date1 = sdf.parse(time1)
            val date2 = sdf.parse(time2)

            if (date1 != null && date2 != null) {
                val diffMillis = kotlin.math.abs(date2.time - date1.time)
                val hours = TimeUnit.MILLISECONDS.toHours(diffMillis)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis) % 60
                val seconds = TimeUnit.MILLISECONDS.toSeconds(diffMillis) % 60

                Snackbar.make(
                    coordinatorLayout,
                    fragmentActivity.getString(
                        R.string.radio_stop_time,
                        hours, minutes, seconds
                    ),
                    Snackbar.LENGTH_LONG
                ).show()

            }
        } catch (e: ParseException) {
            Toast.makeText(
                fragmentActivity,
                fragmentActivity.getString(R.string.time_parse_error),
                Toast.LENGTH_SHORT
            ).show()

        }
    }

}
