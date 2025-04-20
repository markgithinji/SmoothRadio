package com.smoothradio.radio.core;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;


public class PlayerPrefsViewModel extends AndroidViewModel {

    private final PlayerPreferencesRepository prefsRepo;
    private final MutableLiveData<Integer> id = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isFirstTime = new MutableLiveData<>();

    public PlayerPrefsViewModel(@NonNull Application application) {
        super(application);
        prefsRepo = new PlayerPreferencesRepository(application.getApplicationContext());

        id.setValue(prefsRepo.getId());
        isFirstTime.setValue(prefsRepo.isFirstTime());
    }

    public LiveData<Integer> getLogo() {
        return id;
    }
    public void saveId(int value) {
        prefsRepo.setId(value);
        id.setValue(value);
    }

    public LiveData<Boolean> getIsFirstTime() {
        return isFirstTime;
    }

    public void saveIsFirstTime(boolean value) {
        prefsRepo.setFirstTime(value);
        isFirstTime.setValue(value);
    }

    public void clearPrefs() {
        prefsRepo.clear();
    }
}
