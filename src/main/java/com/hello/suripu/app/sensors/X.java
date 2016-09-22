package com.hello.suripu.app.sensors;

import com.fasterxml.jackson.annotation.JsonProperty;

public class X {

    private final long timestamp;
    private final int offsetMillis;

    public X(long timestamp, int offsetMillis) {
        this.timestamp = timestamp;
        this.offsetMillis = offsetMillis;
    }

    @JsonProperty("t")
    public long timestamp() {
        return timestamp;
    }

    @JsonProperty("o")
    public int offsetMillis() {
        return offsetMillis;
    }
}
