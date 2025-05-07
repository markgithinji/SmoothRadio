package com.smoothradio.radio.core.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.smoothradio.radio.core.util.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class PlayerPreferencesRepository {

    private static final String PREF_NAME = "PlayerFragmentSharedPref";
    private static final String KEY_ID = "stationId";


    private final SharedPreferences prefs;
    private final MutableLiveData<Resource<Integer>> stationIdLiveData = new MutableLiveData<>();

    public PlayerPreferencesRepository(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadInitialPreferences();
    }

    private void loadInitialPreferences() {
        int id = prefs.getInt(KEY_ID, 0);

        stationIdLiveData.setValue(Resource.success(id));
    }

    public void setId(int id) {
        prefs.edit().putInt(KEY_ID, id).apply();
        stationIdLiveData.setValue(Resource.success(id));
    }

    public LiveData<Resource<Integer>> getIdLiveData() {
        return stationIdLiveData;
    }

    public void reloadStationId() {
        int id = prefs.getInt(KEY_ID, 0);
        stationIdLiveData.setValue(Resource.success(id));
    }
}

