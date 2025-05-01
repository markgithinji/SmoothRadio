package com.smoothradio.radio.model;

import java.util.Objects;

public class RadioStation {
    int logoResource;
    String stationName;
    String frequency;
    String location;
    String url;
    int id;

    boolean isPlaying;

    public RadioStation(int id, int logoResource, String stationName, String frequency, String location, String url,boolean isPlaying ) {
        this.logoResource = logoResource;
        this.stationName = stationName;
        this.frequency = frequency;
        this.location = location;
        this.url = url;
        this.id = id;
        this.isPlaying = isPlaying;

    }

    public RadioStation(RadioStation other) {
        this.id = other.id;
        this.logoResource = other.logoResource;
        this.stationName = other.stationName;
        this.frequency = other.frequency;
        this.location = other.location;
        this.url = other.url;
        this.isPlaying = other.isPlaying;
    }

    public void setPlaying(boolean playing) {
        isPlaying = playing;
    }

    public boolean isPlaying() {
        return isPlaying;
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
