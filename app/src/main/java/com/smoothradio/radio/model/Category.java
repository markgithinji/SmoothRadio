package com.smoothradio.radio.model;

import java.util.List;

public class Category {
    public String label;
    public List<RadioStation> categoryRadioStationList;

    public Category(String label, List<RadioStation> categoryRadioStationList) {
        this.label = label;
        this.categoryRadioStationList = categoryRadioStationList;
    }

}
