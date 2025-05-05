package com.smoothradio.radio.core.data;

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
    private static final String KEY_IS_FIRST_TIME = "isFirstTime";

    private static final String KEY_PREFIX_FAVORITE = "favouriteStationName";
    private final MutableLiveData<Resource<List<String>>> favoritesLiveData = new MutableLiveData<>();


    private final SharedPreferences prefs;
    private final MutableLiveData<Resource<Integer>> stationIdLiveData = new MutableLiveData<>();
    private final MutableLiveData<Resource<Boolean>> isFirstTimeLiveData = new MutableLiveData<>();

    public PlayerPreferencesRepository(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadInitialPreferences();
        loadFavoriteStationNames();
    }

    private void loadInitialPreferences() {
        int id = prefs.getInt(KEY_ID, 0);
        boolean isFirstTime = prefs.getBoolean(KEY_IS_FIRST_TIME, true);

        stationIdLiveData.setValue(Resource.success(id));
        isFirstTimeLiveData.setValue(Resource.success(isFirstTime));
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
    public void setFirstTime(boolean isFirstTime) {
        prefs.edit().putBoolean(KEY_IS_FIRST_TIME, isFirstTime).apply();
        isFirstTimeLiveData.setValue(Resource.success(isFirstTime));
    }

    public LiveData<Resource<Boolean>> isFirstTimeLiveData() {
        return isFirstTimeLiveData;
    }

    // Favorite stations
    public LiveData<Resource<List<String>>> getFavoriteStationNames() {
        loadFavoriteStationNames(); // Optional: refresh when called
        return favoritesLiveData;
    }

    public void addFavorite(String stationName) {
        prefs.edit().putString(KEY_PREFIX_FAVORITE + stationName, stationName).apply();
        loadFavoriteStationNames();
    }

    public void removeFavorite(String stationName) {
        prefs.edit().remove(KEY_PREFIX_FAVORITE + stationName).apply();
        loadFavoriteStationNames();
    }

    private void loadFavoriteStationNames() {
        favoritesLiveData.setValue(Resource.loading());
        try {
            List<String> favoriteNames = new ArrayList<>();
            Map<String, ?> allPrefs = prefs.getAll();
            for (String key : allPrefs.keySet()) {
                if (key.startsWith(KEY_PREFIX_FAVORITE)) {
                    favoriteNames.add((String) allPrefs.get(key));
                }
            }
            favoritesLiveData.setValue(Resource.success(favoriteNames));
        } catch (Exception e) {
            favoritesLiveData.setValue(Resource.error("Failed to load favorites"));
        }
    }
}

