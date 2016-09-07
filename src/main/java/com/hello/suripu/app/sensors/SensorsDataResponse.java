package com.hello.suripu.app.sensors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Maps;
import com.hello.suripu.core.models.Sensor;

import java.util.ArrayList;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SensorsDataResponse {
    private final Map<Sensor, SensorData> sensorData;
    private final List<X> timestamps;

    private SensorsDataResponse(Map<Sensor, SensorData> sensorData, final List<X> timestamps) {
        this.sensorData = sensorData;
        this.timestamps = timestamps;
    }


    public static SensorsDataResponse ok(final Map<Sensor, SensorData> sensorData, final List<X> timestamps) {
        return new SensorsDataResponse(sensorData, timestamps);
    }

    public static SensorsDataResponse noSense() {
        return new SensorsDataResponse( Maps.newHashMap(), new ArrayList<>());
    }

    public static SensorsDataResponse noData() {
        return new SensorsDataResponse(Maps.newHashMap(), new ArrayList<>());
    }

    @JsonProperty("timestamps")
    public List<X> timestamps() {
        return timestamps;
    }

    @JsonProperty("sensors")
    public Map<String, float[]> sensors() {

        return sensorData.entrySet()
                .stream()
                .collect(
                        Collectors.toMap(
                                e -> e.getKey().name(),
                                e -> e.getValue().values())
                );
    }
}