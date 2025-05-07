package com.smoothradio.radio.core.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.smoothradio.radio.core.data.repository.PlayerPreferencesRepository;
import com.smoothradio.radio.core.data.repository.RadioLinkRepository;
import com.smoothradio.radio.core.data.repository.RadioRepository;
import com.smoothradio.radio.core.util.Resource;
import com.smoothradio.radio.core.model.RadioStation;
import com.smoothradio.radio.service.StreamService;

import java.util.List;

public class RadioViewModel extends AndroidViewModel {
    private final RadioLinkRepository radioLinkRepository;
    private final PlayerPreferencesRepository prefsRepo;
    private final RadioRepository repository;

    private LiveData<Resource<List<String>>> localRadioLinksLiveData;
    private LiveData<Resource<Integer>> stationIdLiveData;
    private LiveData<List<RadioStation>> allStations;
    private final LiveData<List<RadioStation>> favoriteStations;

    private final MutableLiveData<RadioStation> selectedStation = new MutableLiveData<>();
    private final MutableLiveData<Integer> currentPage = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> updateMiniPlayerEvent = new MutableLiveData<>();


    public RadioViewModel(@NonNull Application application) {
        super(application);
        radioLinkRepository = new RadioLinkRepository();
        prefsRepo = new PlayerPreferencesRepository(application.getApplicationContext());
        repository = new RadioRepository(application);

        allStations = repository.getAllStations();
        favoriteStations = repository.getFavoriteStations();
    }

    // -------------------
    // Radio Station Logic
    // -------------------
    public LiveData<List<RadioStation>> getAllStations() {
        return allStations;
    }
    public LiveData<List<RadioStation>> getFavoriteStations() {
        return favoriteStations;
    }
    public void savePlayingStationId(int id) {
        repository.setPlayingStation(id);
    }
    public LiveData<RadioStation> getPlayingStation() {
        return repository.getPlayingStation();
    }
    public void insertStations(List<RadioStation> stations) {
        repository.insertStations(stations);
    }

    public void updateFavoriteStatus(int id, boolean isFav) {
        repository.updateFavoriteStatus(id, isFav);
    }
    // -----------------------
    // Radio Links Logic
    // -----------------------

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


    // -----------------------
    // PLayer Logic
    // -----------------------
    public void setSelectedStation(RadioStation station) {
        selectedStation.setValue(station);
    }

    public LiveData<RadioStation> getSelectedStation() {
        return selectedStation;
    }

    //-------------------------
    // ViewPager Current Page Logic
    //-------------------------
    public LiveData<Integer> getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int page) {
        currentPage.setValue(page);
    }

    //------------------------
    // Update Mini Player Trigger
    //------------------------
    public LiveData<Boolean> getOnRemoteLinksLoadedEvent() {
        return updateMiniPlayerEvent;
    }

    // Call this when remote links are loaded successfully
    public void onRemoteLinksLoaded() {
        updateMiniPlayerEvent.setValue(true);
    }
}
