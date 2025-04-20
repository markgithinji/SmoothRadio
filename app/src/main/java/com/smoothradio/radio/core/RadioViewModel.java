package com.smoothradio.radio.core;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;

public class RadioViewModel extends AndroidViewModel {

    private final RadioLinkRepository radioLinkRepository;
    private final PlayerPreferencesRepository prefsRepo;

    private final MutableLiveData<List<String>> radioLinksLiveData = new MutableLiveData<>();
    private final MutableLiveData<Integer> stationIdLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isFirstTimeLiveData = new MutableLiveData<>();

    public RadioViewModel(@NonNull Application application) {
        super(application);
        radioLinkRepository = new RadioLinkRepository(application.getApplicationContext());
        prefsRepo = new PlayerPreferencesRepository(application.getApplicationContext());

        // Init preference values
        stationIdLiveData.setValue(prefsRepo.getId());
        isFirstTimeLiveData.setValue(prefsRepo.isFirstTime());
    }

    // -----------------------
    // Radio Links Logic
    // -----------------------

    public void createInitialLinks() {
        radioLinkRepository.createInitialTxt();
    }

    public void loadLinks() {
        List<String> links = radioLinkRepository.loadLinks();
        radioLinksLiveData.setValue(links);
    }

    public void updateLinks(List<String> newLinks) {
        radioLinkRepository.updateLinks(new ArrayList<>(newLinks));
        radioLinksLiveData.setValue(newLinks);
    }

    public LiveData<List<String>> getRadioLinksLiveData() {
        return radioLinksLiveData;
    }
    public LiveData<Resource<List<String>>> getRemoteStreamLinks() {
        return radioLinkRepository.getRemoteStreamLinks();
    }
    public void removeStreamLinkListener() {
        radioLinkRepository.removeListener();
    }
    @Override
    protected void onCleared() {
        super.onCleared();
        radioLinkRepository.removeListener();
    }
    // -----------------------
    // Preferences Logic
    // -----------------------

    public LiveData<Integer> getStationId() {
        return stationIdLiveData;
    }

    public void saveStationId(int id) {
        prefsRepo.setId(id);
        stationIdLiveData.setValue(id);
    }

    public LiveData<Boolean> getIsFirstTime() {
        return isFirstTimeLiveData;
    }

    public void saveIsFirstTime(boolean value) {
        prefsRepo.setFirstTime(value);
        isFirstTimeLiveData.setValue(value);
    }

    public void clearPrefs() {
        prefsRepo.clear();
    }
}
