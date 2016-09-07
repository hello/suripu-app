package com.hello.suripu.app.sensors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hello.suripu.core.models.Sample;

import java.util.List;
import java.util.stream.Collectors;

public class SensorData {
    private final List<SampleView> values;

    private SensorData(final List<SampleView> values) {
        this.values = values;
    }

    public static SensorData from(SensorUnit unit, List<Sample> values) {
        final List<SampleView> views = values
                .stream()
                .map(sample -> SampleViewV2.from(sample))
                .collect(Collectors.toList());
        return new SensorData(views);
    }

    @JsonProperty("values")
    public List<SampleView> values() {
        return values;
    }
}
