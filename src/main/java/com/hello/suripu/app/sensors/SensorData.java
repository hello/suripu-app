package com.hello.suripu.app.sensors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.primitives.Floats;
import com.hello.suripu.core.models.Sample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

public class SensorData {
    private final float[] values;
    private static final Logger LOGGER = LoggerFactory.getLogger(SensorData.class);

    private SensorData(final float[] values) {
        this.values = values;
    }

    public static SensorData from(final List<Sample> values) {
        if(values == null) {
            return new SensorData(new float[]{});
        }

        final List<Float> views = values
                .stream()
                .map(sample -> parse(sample))
                .collect(Collectors.toList());
        return new SensorData(Floats.toArray(views));
    }


    public static float parse(Sample sample) {
        if(sample == null) {
            return -1f;
        }
        try {
            return new BigDecimal(String.valueOf(sample.value)).setScale(1, BigDecimal.ROUND_HALF_UP).floatValue();
        } catch (Exception e) {
            LOGGER.warn("error=parsing-float msg={}", e.getMessage());
        }
        return -1;
    }

    @JsonProperty("values")
    public float[] values() {
        return values;
    }
}