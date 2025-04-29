package com.smoothradio.radio.core.util;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.Toast;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.smoothradio.radio.service.StreamService;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class TimerSetterHelper {
    private final FragmentActivity fragmentActivity;
    private final CoordinatorLayout coordinatorLayout;

    private String time1;
    private String time2;
    private final Intent setTimerIntent = new Intent();

    public TimerSetterHelper(FragmentActivity fragmentActivity, CoordinatorLayout coordinatorLayout) {
        this.fragmentActivity = fragmentActivity;
        this.coordinatorLayout = coordinatorLayout;
    }

    public void showTimerPicker() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        time1 = java.text.DateFormat.getTimeInstance().format(calendar.getTime());

        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setHour(hour)
                .setMinute(minute)
                .setTitleText("Set Time To Turn Off Radio")
                .build();

        picker.show(fragmentActivity.getSupportFragmentManager(), "SetTimerFrag");

        picker.addOnPositiveButtonClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleTimerSet(picker);
            }
        });
    }

    private void handleTimerSet(MaterialTimePicker picker) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, picker.getHour());
        calendar.set(Calendar.MINUTE, picker.getMinute());
        calendar.set(Calendar.SECOND, 0);

        long timeInMillis = calendar.getTimeInMillis();
        setTimerIntent.putExtra(StreamService.EXTRA_TIME_IN_MILLIS, timeInMillis);
        setTimerIntent.setAction(StreamService.ACTION_SET_TIMER);
        setTimerIntent.setPackage(fragmentActivity.getPackageName());
        fragmentActivity.sendBroadcast(setTimerIntent);

        time2 = java.text.DateFormat.getTimeInstance().format(calendar.getTime());
        showTimeDifference();
    }
    private void showTimeDifference() {
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("hh:mm:ss aa", Locale.getDefault());
            Date date1 = simpleDateFormat.parse(time1);
            Date date2 = simpleDateFormat.parse(time2);

            if (date1 != null && date2 != null) {
                long differenceInMilliSeconds = Math.abs(date2.getTime() - date1.getTime());

                long hours = TimeUnit.MILLISECONDS.toHours(differenceInMilliSeconds);
                long minutes = TimeUnit.MILLISECONDS.toMinutes(differenceInMilliSeconds) % 60;
                long seconds = TimeUnit.MILLISECONDS.toSeconds(differenceInMilliSeconds) % 60;

                Snackbar.make(coordinatorLayout,
                        "Radio will Stop after " + hours + " Hours "
                                + minutes + " Minutes "
                                + seconds + " Seconds.",
                        Snackbar.LENGTH_LONG
                ).show();
            }
        } catch (ParseException e) {
            Toast.makeText(fragmentActivity, "Failed to parse time", Toast.LENGTH_SHORT).show();
        }
    }
}
