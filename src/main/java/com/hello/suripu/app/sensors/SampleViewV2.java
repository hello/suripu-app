package com.hello.suripu.app.sensors;

import com.hello.suripu.core.models.Sample;

public class SampleViewV2 implements SampleView {

    private final long timestamp;
    private final float value;
    private final int offsetMillis;


    private SampleViewV2(long timestamp, float value, int offsetMillis) {
        this.timestamp = timestamp;
        this.value = value;
        this.offsetMillis = offsetMillis;
    }

    public static SampleView from(Sample sample) {
        return new SampleViewV2(sample.dateTime, sample.value, sample.offsetMillis);
    }

    @Override
    public long timestamp() {
        return timestamp;
    }

    @Override
    public float value() {
        return value;
    }

    @Override
    public int offsetMillis() {
        return offsetMillis;
    }
}
