package com.smoothradio.radio.model;

import java.util.Objects;

public class RadioStation {
    int smallLogo;
    String stationName;
    String frequency;
    String location;
    String url;
    int id;

    public RadioStation(int smallLogo, String stationName, String frequency, String location, String url, int id) {
        this.smallLogo = smallLogo;
        this.stationName = stationName;
        this.frequency = frequency;
        this.location = location;
        this.url = url;
        this.id = id;

    }

    public int getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public int getSmallLogo() {
        return smallLogo;
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
