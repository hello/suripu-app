package com.hello.suripu.app.sensors;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class SensorResponse {
    private final SensorStatus status;
    private final List<SensorView> sensorViews;

    public SensorResponse(SensorStatus status, List<SensorView> sensorViews) {
        this.status = status;
        this.sensorViews = sensorViews;
    }

    @JsonProperty
    public SensorStatus status() {
        return status;
    }

    @JsonProperty
    public List<SensorView> sensors() {
        return sensorViews;
    }
}
