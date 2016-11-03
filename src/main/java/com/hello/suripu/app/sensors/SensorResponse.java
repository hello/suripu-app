package com.hello.suripu.app.sensors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.hello.suripu.core.roomstate.Condition;

import java.util.List;
import java.util.stream.Collectors;

public class SensorResponse {
    private final SensorStatus status;
    private final List<SensorView> sensorViews;
    private final Condition condition;

    public SensorResponse(SensorStatus status, List<SensorView> sensorViews, final Condition condition) {
        this.status = status;
        this.sensorViews = sensorViews;
        this.condition = condition;
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

    @JsonIgnore
    public Condition condition() { return condition; }

    public static SensorResponse noData(final List<SensorView> sensorViews) {
        return new SensorResponse(SensorStatus.WAITING_FOR_DATA, sensorViews, Condition.UNKNOWN);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(SensorResponse.class)
                .add("status", status)
                .add("views", sensorViews)
                .toString();
    }
}