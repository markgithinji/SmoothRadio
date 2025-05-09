package com.smoothradio.radio.feature.player.util

import android.content.Intent
import android.view.View
import android.widget.Toast
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.FragmentActivity
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.smoothradio.radio.service.StreamService
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class TimerSetterHelper(
    private val fragmentActivity: FragmentActivity,
    private val coordinatorLayout: CoordinatorLayout
) {
    private var time1: String? = null
    private var time2: String? = null
    private val setTimerIntent = Intent()

    fun showTimerPicker() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        time1 = android.text.format.DateFormat.getTimeFormat(fragmentActivity).format(calendar.time)

        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(hour)
            .setMinute(minute)
            .setTitleText("Set Time To Turn Off Radio")
            .build()

        picker.show(fragmentActivity.supportFragmentManager, "SetTimerFrag")

        picker.addOnPositiveButtonClickListener {
            handleTimerSet(picker)
        }
    }

    private fun handleTimerSet(picker: MaterialTimePicker) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, picker.hour)
            set(Calendar.MINUTE, picker.minute)
            set(Calendar.SECOND, 0)
        }

        val timeInMillis = calendar.timeInMillis
        setTimerIntent.putExtra(StreamService.EXTRA_TIME_IN_MILLIS, timeInMillis)
        setTimerIntent.action = StreamService.ACTION_SET_TIMER
        setTimerIntent.setPackage(fragmentActivity.packageName)
        fragmentActivity.sendBroadcast(setTimerIntent)

        time2 = android.text.format.DateFormat.getTimeFormat(fragmentActivity).format(calendar.time)
        showTimeDifference()
    }

    private fun showTimeDifference() {
        try {
            val simpleDateFormat = SimpleDateFormat("hh:mm:ss aa", Locale.getDefault())
            val date1: Date? = simpleDateFormat.parse(time1 ?: "")
            val date2: Date? = simpleDateFormat.parse(time2 ?: "")

            if (date1 != null && date2 != null) {
                val differenceInMillis = kotlin.math.abs(date2.time - date1.time)

                val hours = TimeUnit.MILLISECONDS.toHours(differenceInMillis)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(differenceInMillis) % 60
                val seconds = TimeUnit.MILLISECONDS.toSeconds(differenceInMillis) % 60

                Snackbar.make(
                    coordinatorLayout,
                    "Radio will Stop after $hours Hours $minutes Minutes $seconds Seconds.",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        } catch (e: ParseException) {
            Toast.makeText(fragmentActivity, "Failed to parse time", Toast.LENGTH_SHORT).show()
        }
    }
}
