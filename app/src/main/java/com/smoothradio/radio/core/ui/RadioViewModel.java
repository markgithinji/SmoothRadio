package com.smoothradio.radio.core.ui;

import android.app.Application;

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
    private LiveData<Resource<List<String>>> localRadioLinksLiveData;
    private LiveData<Resource<Boolean>> isFirstTimeLiveData;
    private LiveData<Resource<Integer>> stationIdLiveData;
    private final MutableLiveData<RadioStation> selectedStation = new MutableLiveData<>();
    private final MutableLiveData<String> streamState = new MutableLiveData<>(StreamService.StreamStates.IDLE);
    private final MutableLiveData<Integer> currentPage = new MutableLiveData<>(0);


    public RadioViewModel(@NonNull Application application) {
        super(application);
        radioLinkRepository = new RadioLinkRepository(application.getApplicationContext());
        prefsRepo = new PlayerPreferencesRepository(application.getApplicationContext());



    }

    // -----------------------
    // Radio Links Logic
    // -----------------------

    // Create default file (e.g. first time launch)
    public void createInitialLinks() {
        radioLinkRepository.createInitialTxt();
    }

    // Fetch remote links from Firestore and update file only if needed
    public void getRemoteLinks() {
        localRadioLinksLiveData = radioLinkRepository.getRemoteStreamLinks();
    }

    public LiveData<Resource<List<String>>> getRemoteLinksLiveData() {
        return localRadioLinksLiveData;
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
    public void reloadStationId() {
        prefsRepo.reloadStationId();
    }

    public void isFirstTime() {
        isFirstTimeLiveData = prefsRepo.isFirstTimeLiveData();
    }
    public LiveData<Resource<Boolean>> getIsFirstTimeLiveData() {
        return isFirstTimeLiveData;
    }
    private final MutableLiveData<RadioStation> stationUpdate = new MutableLiveData<>();

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

        return selectedStation;
    }

    public LiveData<String> getStreamState() {
        return streamState;
    }

    public void setStreamState(String state) {
        streamState.setValue(state);
    }

    //-------------------------
    // Favourites Logic
    //-------------------------
    // Add a station name to favorites
    public void addToFavorites(String stationName) {
        prefsRepo.addFavorite(stationName);
    }

    // Remove a station name from favorites
    public void removeFromFavorites(String stationName) {
        prefsRepo.removeFavorite(stationName);
    }

    // Observe favorite names list
    public LiveData<Resource<List<String>>> getFavoriteStationNames() {
        return prefsRepo.getFavoriteStationNames();
    }

    //-------------------------
    // Pager Logic
    //-------------------------
    public LiveData<Integer> getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int page) {
        currentPage.setValue(page);
    }

}
