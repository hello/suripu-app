package com.hello.suripu.app.sensors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.hello.suripu.core.roomstate.Condition;

public class ScaleInterval {
    private final String name;
    private final Integer min;
    private final Integer max;
    private final Condition condition;

    public ScaleInterval(String name, Integer min, Integer max, Condition condition) {
        this.name = name;
        this.min = min;
        this.max = max;
        this.condition = condition;
    }

    @JsonProperty("name")
    public String name() {
        return name;
    }

    @JsonProperty("min")
    public Integer min() {
        return min;
    }

    @JsonProperty("max")
    public Integer max() {
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
