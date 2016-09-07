package com.hello.suripu.app.sensors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.primitives.Floats;
import com.hello.suripu.core.models.Sample;

import java.util.List;
import java.util.stream.Collectors;

public class SensorData {
    private final float[] values;

    private SensorData(final float[] values) {
        this.values = values;
    }

    public static SensorData from(List<Sample> values) {
        final List<Float> views = values
                .stream()
                .map(sample -> sample.value)
                .collect(Collectors.toList());
        return new SensorData(Floats.toArray(views));
    }

    @JsonProperty("values")
    public float[] values() {
        return values;
    }
}
