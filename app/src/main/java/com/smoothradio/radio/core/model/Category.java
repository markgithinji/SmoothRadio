package com.smoothradio.radio.core.model;

import java.util.List;

public class Category {
    private final String label;
    private final List<RadioStation> categoryRadioStationList;

    public Category(String label, List<RadioStation> categoryRadioStationList) {
        this.label = label;
        this.categoryRadioStationList = categoryRadioStationList;
    }

    public String getLabel() {
        return label;
    }

    public List<RadioStation> getCategoryRadioStationList() {
        return categoryRadioStationList;
    }
}

