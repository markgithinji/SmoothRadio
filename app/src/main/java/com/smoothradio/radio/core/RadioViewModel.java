package com.smoothradio.radio.core;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import java.util.List;

public class RadioViewModel extends AndroidViewModel {

    private final RadioLinkRepository radioLinkRepository;
    private final PlayerPreferencesRepository prefsRepo;

    private LiveData<Resource<List<String>>> remoteRadioLinksLiveData;
    private LiveData<Resource<List<String>>> localRadioLinksLiveData;
    private LiveData<Resource<Boolean>> isFirstTimeLiveData;
    private LiveData<Resource<Integer>> stationIdLiveData;


    public RadioViewModel(@NonNull Application application) {
        super(application);
        radioLinkRepository = new RadioLinkRepository(application.getApplicationContext());
        prefsRepo = new PlayerPreferencesRepository(application.getApplicationContext());

        // Init radio links
        isFirstTime();
        getStationId();
        getLocalLinks();
        getRemoteStreamLinks();

    }

    // -----------------------
    // Radio Links Logic
    // -----------------------

    public void createInitialLinks() {
        radioLinkRepository.createInitialTxt();
    }

    public void getLocalLinks() {
        localRadioLinksLiveData = radioLinkRepository.getLocalLinks();
    }
    public void getRemoteStreamLinks() {
        remoteRadioLinksLiveData = radioLinkRepository.getRemoteStreamLinks();
    }

    public LiveData<Resource<List<String>>> getRemoteinksLiveData() {
        return remoteRadioLinksLiveData;
    }
    public LiveData<Resource<List<String>>> getLocalLinksLiveData() {
        return remoteRadioLinksLiveData;
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

    public LiveData<Resource<Integer>> getStationIdLivedata() {
        return stationIdLiveData;
    }

    public void getStationId() {
        stationIdLiveData = prefsRepo.getIdLiveData();
    }

    public void saveStationId(int id) {
        prefsRepo.setId(id);
    }

    public void isFirstTime() {
        isFirstTimeLiveData = prefsRepo.isFirstTimeLiveData();
    }
    public LiveData<Resource<Boolean>> getIsFirstTimeLiveData() {
        return isFirstTimeLiveData;
    }

    public void saveIsFirstTime(boolean value) {
        prefsRepo.setFirstTime(value);
    }
}
