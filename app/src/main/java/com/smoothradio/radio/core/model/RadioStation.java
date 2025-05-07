package com.smoothradio.radio.core.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Objects;

@Entity(tableName = "radio_stations")
public class RadioStation implements ListItem{
    @PrimaryKey
    private int id;
    private int logoResource;
    private String stationName;
    private String frequency;
    private String location;
    private String url;

    private boolean isPlaying;
    private boolean isFavorite;

    public RadioStation(int id, int logoResource, String stationName, String frequency, String location, String url, boolean isPlaying, boolean isFavorite) {
        this.logoResource = logoResource;
        this.stationName = stationName;
        this.frequency = frequency;
        this.location = location;
        this.url = url;
        this.id = id;
        this.isPlaying = isPlaying;
        this.isFavorite = isFavorite;
    }


    public RadioStation(RadioStation other) {
        this.id = other.id;
        this.logoResource = other.logoResource;
        this.stationName = other.stationName;
        this.frequency = other.frequency;
        this.location = other.location;
        this.url = other.url;
        this.isPlaying = other.isPlaying;
        this.isFavorite = other.isFavorite;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public void setPlaying(boolean playing) {
        isPlaying = playing;
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public void setFavorite(boolean favorite) {
        isFavorite = favorite;
    }

    public int getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public int getLogoResource() {
        return logoResource;
    }

    public String getStationName() {
        return stationName;
    }

    public String getFrequency() {
        return frequency;
    }

    public String getLocation() {
        return location;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RadioStation that = (RadioStation) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
