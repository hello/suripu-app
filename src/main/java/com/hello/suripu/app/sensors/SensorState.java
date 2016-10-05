package com.hello.suripu.app.sensors;

import com.hello.suripu.core.roomstate.Condition;

public class SensorState {
    public final Float value;
    public final String message;
    public final Condition condition;

    public SensorState(final Float value, final String message, final Condition condition) {
        this.value = value;
        this.message = message;
        this.condition = condition;
    }
}
