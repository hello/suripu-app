package com.hello.suripu.app.sensors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hello.suripu.core.models.CurrentRoomState;
import com.hello.suripu.core.models.Sensor;

import java.util.List;

public class SensorView {
    private final String name;
    private final Sensor type;
    private final SensorUnit unit;
    private final String message;
    private final Scale scale;
    private final CurrentRoomState.State.Condition condition;

    public SensorView(String name, Sensor type, SensorUnit unit, String message, CurrentRoomState.State.Condition condition, Scale scale) {
        this.name = name;
        this.type = type;
        this.unit = unit;
        this.message = message;
        this.scale = scale;
        this.condition = condition;
    }

    @JsonProperty("name")
    public String name() {
        return name;
    }

    @JsonProperty("type")
    public String type() {
        return type.name();
    }

    @JsonProperty("unit")
    public SensorUnit unit() {
        return unit;
    }

    @JsonProperty("message")
    public String message() {
        return message;
    }

    @JsonProperty("scale")
    public List<ScaleInterval> scale() {
        return scale.intervals();
    }

    @JsonProperty("condition")
    public CurrentRoomState.State.Condition condition() {
        return condition;
    }
}
