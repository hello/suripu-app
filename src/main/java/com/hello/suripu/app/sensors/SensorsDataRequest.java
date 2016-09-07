package com.hello.suripu.app.sensors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

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

}
