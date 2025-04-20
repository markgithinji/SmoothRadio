package com.smoothradio.radio.core;

import android.content.Context;
import android.content.SharedPreferences;

public class PlayerPreferencesRepository {

    private static final String PREF_NAME = "PlayerFragmentSharedPref";
    private static final String kEY_ID = "stationId";
    private static final String KEY_IS_FIRST_TIME = "isFirstTime";

    private final SharedPreferences prefs;
    private final SharedPreferences.Editor editor;

    public PlayerPreferencesRepository(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
    }

    public void setId(int logo) {
        editor.putInt(kEY_ID, logo).apply();
    }

    public int getId() {
        return prefs.getInt(kEY_ID, 0);
    }

    public void setFirstTime(boolean isFirstTime) {
        editor.putBoolean(KEY_IS_FIRST_TIME, isFirstTime).apply();
    }

    public boolean isFirstTime() {
        return prefs.getBoolean(KEY_IS_FIRST_TIME, true);
    }

    public void clear() {
        editor.clear().apply();
    }
}
