package com.hello.suripu.app.sensors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;

import java.util.List;
import java.util.stream.Collectors;

public class SensorResponse {
    private final SensorStatus status;
    private final List<SensorView> sensorViews;

    public SensorResponse(SensorStatus status, List<SensorView> sensorViews) {
        this.status = status;
        this.sensorViews = sensorViews;
    }

    @JsonIgnore
    public List<String> availableSensors() {
        return sensorViews.stream().map(s -> s.name()).collect(Collectors.toList());
    }

    @JsonProperty
    public SensorStatus status() {
        return status;
    }

    @JsonProperty
    public List<SensorView> sensors() {
        return sensorViews;
    }

    public static SensorResponse noData(final List<SensorView> sensorViews) {
        return new SensorResponse(SensorStatus.WAITING_FOR_DATA, sensorViews);
    }


    @Override
    public String toString() {
        return MoreObjects.toStringHelper(SensorResponse.class)
                .add("status", status)
                .add("views", sensorViews)
                .toString();
    }
}