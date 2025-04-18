package com.smoothradio.radio.model;

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
    public boolean equals(Object other) {
        if (!(other instanceof RadioStation))
            return false;
        RadioStation p = (RadioStation) other;
        return p.getId() == this.getId();
    }


}
