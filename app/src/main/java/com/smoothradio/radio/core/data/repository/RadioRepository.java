package com.smoothradio.radio.core.data.repository;

import android.app.Application;
import androidx.lifecycle.LiveData;

import com.smoothradio.radio.core.data.local.AppDatabase;
import com.smoothradio.radio.core.data.local.RadioStationDao;
import com.smoothradio.radio.core.model.RadioStation;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RadioRepository {
    private RadioStationDao dao;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    public RadioRepository(Application app) {
        AppDatabase db = AppDatabase.getInstance(app);
        dao = db.radioStationDao();
    }

    public LiveData<List<RadioStation>> getAllStations() {
        return dao.getAllStations();
    }

    public LiveData<List<RadioStation>> getFavoriteStations() {
        return dao.getFavoriteStations();
    }

    public void setPlayingStation(int id) {
        executor.execute(() -> {
            dao.clearPlayingState();
            dao.updatePlayingStation(id);
        });
    }

    public LiveData<RadioStation> getPlayingStation() {
        return dao.getPlayingStation();
    }

    public void insertStations(List<RadioStation> stations) {
        executor.execute(() -> dao.insertStations(stations));
    }

    public void updateFavoriteStatus(int id, boolean isFav) {
        executor.execute(() -> dao.updateFavoriteStatus(id, isFav));
    }
}
