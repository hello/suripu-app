package com.hello.suripu.app.sensors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;

import java.util.List;

public class SensorsDataRequest {
    private final List<SensorQuery> sensorQueries;

    private SensorsDataRequest(List<SensorQuery> sensorQueries) {
        this.sensorQueries = sensorQueries;
    }

    @JsonCreator
    public static SensorsDataRequest create(
            @JsonProperty("sensors") List<SensorQuery> sensorQueries) {
        return new SensorsDataRequest(sensorQueries);
    }

    public List<SensorQuery> queries() {
        return sensorQueries;
    }


    @Override
    public String toString() {
        return MoreObjects.toStringHelper(SensorsDataRequest.class)
                .add("queries", sensorQueries)
                .toString();
    }
}
