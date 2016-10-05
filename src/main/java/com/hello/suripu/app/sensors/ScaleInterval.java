package com.hello.suripu.app.sensors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.hello.suripu.core.roomstate.Condition;

public class ScaleInterval {
    private final String name;
    private final String message;
    private final Float min;
    private final Float max;
    private final Condition condition;

    public ScaleInterval(String name, String message, Float min, Float max, Condition condition) {
        this.name = name;
        this.message = message;
        this.min = min;
        this.max = max;
        this.condition = condition;
    }

    @JsonProperty("name")
    public String name() {
        return name;
    }

    @JsonIgnore
    public String message() {
        return message;
    }

    @JsonProperty("min")
    public Float min() {
        return min;
    }

    @JsonProperty("max")
    public Float max() {
        return max;
    }

    @JsonProperty("condition")
    public Condition condition() {
        return condition;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(ScaleInterval.class)
                .add("name", name)
                .add("min", min)
                .add("max", max)
                .add("condition", condition)
                .toString();
    }
}
