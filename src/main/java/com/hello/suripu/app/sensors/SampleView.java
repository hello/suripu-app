package com.hello.suripu.app.sensors;

import com.fasterxml.jackson.annotation.JsonProperty;

public interface SampleView {

    @JsonProperty("timestamp")
    long timestamp();

    @JsonProperty("value")
    float value();

    @JsonProperty("offset_millis")
    int offsetMillis();
}
