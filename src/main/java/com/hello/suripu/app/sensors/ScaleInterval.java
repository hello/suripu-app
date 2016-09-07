package com.hello.suripu.app.sensors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hello.suripu.core.models.CurrentRoomState;

public class ScaleInterval {
    private final String name;
    private final Integer min;
    private final Integer max;
    private final CurrentRoomState.State.Condition condition;

    public ScaleInterval(String name, Integer min, Integer max, CurrentRoomState.State.Condition condition) {
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
    public CurrentRoomState.State.Condition condition() {
        return condition;
    }
}
