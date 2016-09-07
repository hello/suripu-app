package com.hello.suripu.app.sensors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hello.suripu.core.models.Sensor;

import java.util.Map;
import java.util.stream.Collectors;

public class SensorsDataResponse {
    private final Map<Sensor, SensorData> sensorData;

    private SensorsDataResponse(Map<Sensor, SensorData> sensorData) {
        this.sensorData = sensorData;
    }

    public static SensorsDataResponse create(Map<Sensor, SensorData> sensorData) {
        return new SensorsDataResponse(sensorData);
    }

    @JsonProperty("sensors")
    public Map<String, SensorData> sensors() {

        return sensorData.entrySet()
                .stream()
                .collect(
                    Collectors.toMap(
                            e -> e.getKey().name(),
                            e -> e.getValue())
                );
    }
}
