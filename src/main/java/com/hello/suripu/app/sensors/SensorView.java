package com.hello.suripu.app.sensors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.hello.suripu.core.models.Sensor;
import com.hello.suripu.core.roomstate.Condition;

import java.util.List;

public class SensorView {
    private final String name;
    private final Sensor type;
    private final SensorUnit unit;
    private final String message;
    private final Scale scale;
    private final Condition condition;
    private final Float value;

    public SensorView(String name, Sensor type, SensorUnit unit, Float value, String message, Condition condition, Scale scale) {
        this.name = name;
        this.type = type;
        this.unit = unit;
        this.value = value;
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
        return message.replace("*","");
    }

    @JsonProperty("scale")
    public List<ScaleInterval> scale() {
        return scale.intervals();
    }

    @JsonProperty("condition")
    public Condition condition() {
        return condition;
    }

    @JsonProperty("value")
    public Float value() {
        return value;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(SensorView.class)
                .add("name", name)
                .add("type", type)
                .add("unit", unit)
                .add("message", message)
                .add("scale", scale)
                .add("condition", condition)
                .add("value", value)
                .toString();
    }

    public static SensorView from(String name, Sensor type, SensorUnit unit, Scale scale, SensorState state) {
        return new SensorView(name, type, unit, state.value, state.message, state.condition, scale);
    }
}