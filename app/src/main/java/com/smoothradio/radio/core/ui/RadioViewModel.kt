package com.smoothradio.radio.core.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.smoothradio.radio.core.data.repository.RadioLinkRepository;
import com.smoothradio.radio.core.data.repository.RadioRepository;
import com.smoothradio.radio.core.util.Resource;
import com.smoothradio.radio.core.model.RadioStation;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class RadioViewModel extends AndroidViewModel {
    private final RadioLinkRepository radioLinkRepository;
    private final RadioRepository repository;
    private LiveData<Resource<List<String>>> localRadioLinksLiveData;
    private LiveData<List<RadioStation>> allStations;
    private final LiveData<List<RadioStation>> favoriteStations;
    private final MutableLiveData<RadioStation> selectedStation = new MutableLiveData<>();
    private final MutableLiveData<Integer> currentPage = new MutableLiveData<>(0);


    @Inject
    public RadioViewModel(@NonNull Application application,
                          RadioLinkRepository radioLinkRepository,
                          RadioRepository repository) {
        super(application);
        this.radioLinkRepository = radioLinkRepository;
        this.repository = repository;

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
}
