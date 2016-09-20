package com.hello.suripu.app.sensors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Maps;
import com.hello.suripu.core.models.Sensor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BatchQueryResponse {
    private final Map<Sensor, SensorData> sensorData;
    private final List<X> timestamps;

    private BatchQueryResponse(Map<Sensor, SensorData> sensorData, final List<X> timestamps) {
        this.sensorData = sensorData;
        this.timestamps = timestamps;
    }

    public static BatchQueryResponse ok(final Map<Sensor, SensorData> sensorData, final List<X> timestamps) {
        return new BatchQueryResponse(sensorData, timestamps);
    }

    public static BatchQueryResponse noSense() {
        return new BatchQueryResponse( Maps.newHashMap(), new ArrayList<>());
    }

    public static BatchQueryResponse noData() {
        return new BatchQueryResponse(Maps.newHashMap(), new ArrayList<>());
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