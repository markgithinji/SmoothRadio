package com.smoothradio.radio.core;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;


public class PlayerPreferencesRepository {

    private static final String PREF_NAME = "PlayerFragmentSharedPref";
    private static final String KEY_ID = "stationId";
    private static final String KEY_IS_FIRST_TIME = "isFirstTime";

    private final SharedPreferences prefs;

    private final MutableLiveData<Resource<Integer>> stationIdLiveData = new MutableLiveData<>();
    private final MutableLiveData<Resource<Boolean>> isFirstTimeLiveData = new MutableLiveData<>();

    public PlayerPreferencesRepository(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void setId(int id) {
        prefs.edit().putInt(KEY_ID, id).apply();
        stationIdLiveData.setValue(Resource.success(id));
    }

    public LiveData<Resource<Integer>> getIdLiveData() {
        setId(prefs.getInt(KEY_ID, 0));
        return stationIdLiveData;
    }

    public void setFirstTime(boolean isFirstTime) {
        prefs.edit().putBoolean(KEY_IS_FIRST_TIME, isFirstTime).apply();
        isFirstTimeLiveData.setValue(Resource.success(isFirstTime));
    }

    public LiveData<Resource<Boolean>> isFirstTimeLiveData() {
        setFirstTime(prefs.getBoolean(KEY_IS_FIRST_TIME, true));
        return isFirstTimeLiveData;
    }
}
