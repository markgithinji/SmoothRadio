package com.smoothradio.radio.core.ui;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.smoothradio.radio.core.data.PlayerPreferencesRepository;
import com.smoothradio.radio.core.data.RadioLinkRepository;
import com.smoothradio.radio.core.util.Resource;
import com.smoothradio.radio.model.RadioStation;
import com.smoothradio.radio.service.StreamService;

import java.util.List;

public class RadioViewModel extends AndroidViewModel {

    private final RadioLinkRepository radioLinkRepository;
    private final PlayerPreferencesRepository prefsRepo;

    private LiveData<Resource<List<String>>> remoteRadioLinksLiveData;
    private LiveData<Resource<List<String>>> localRadioLinksLiveData;
    private LiveData<Resource<Boolean>> isFirstTimeLiveData;
    private LiveData<Resource<Integer>> stationIdLiveData;
    private final MutableLiveData<RadioStation> selectedStation = new MutableLiveData<>();
    private final MutableLiveData<String> streamState = new MutableLiveData<>(StreamService.StreamStates.IDLE);


    public RadioViewModel(@NonNull Application application) {
        super(application);
        radioLinkRepository = new RadioLinkRepository(application.getApplicationContext());
        prefsRepo = new PlayerPreferencesRepository(application.getApplicationContext());



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

    // -----------------------
    // PLayer Logic
    // -----------------------
    public void setSelectedStation(RadioStation station) {
        selectedStation.setValue(station);
    }

    public LiveData<RadioStation> getSelectedStation() {
        Log.d("ViewModel", "setSelectedStation: ");
        return selectedStation;
    }



    public LiveData<String> getStreamState() {
        return streamState;
    }

    public void setStreamState(String state) {
        streamState.setValue(state);
    }
}
