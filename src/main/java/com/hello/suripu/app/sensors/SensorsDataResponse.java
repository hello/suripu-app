package com.hello.suripu.app.sensors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Maps;
import com.hello.suripu.core.models.Sensor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SensorsDataResponse {
    private final SensorStatus status;
    private final Map<Sensor, SensorData> sensorData;
    private final List<X> timestamps;

    private SensorsDataResponse(SensorStatus status, Map<Sensor, SensorData> sensorData, final List<X> timestamps) {
        this.status = status;
        this.sensorData = sensorData;
        this.timestamps = timestamps;
    }

    public static SensorsDataResponse ok(final Map<Sensor, SensorData> sensorData, final List<X> timestamps) {
        return new SensorsDataResponse(SensorStatus.OK, sensorData, timestamps);
    }

    public static SensorsDataResponse noSense() {
        return new SensorsDataResponse(SensorStatus.OK, Maps.newHashMap(), new ArrayList<>());
    }

    public static SensorsDataResponse noData() {
        return new SensorsDataResponse(SensorStatus.WAITING_FOR_DATA, Maps.newHashMap(), new ArrayList<>());
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
