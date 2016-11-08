package com.hello.suripu.app.sensors;

import com.hello.suripu.core.roomstate.Condition;

public class SensorState {

    private static String UNKNOWN_MESSAGE = "";

    public final Float value;
    public final String message;
    public final Condition condition;

    public SensorState(final Float value, final String message, final Condition condition) {
        this.value = value;
        this.message = message;
        this.condition = condition;
    }

    public static SensorState unknown() {
        return new SensorState(null, UNKNOWN_MESSAGE, Condition.UNKNOWN);
    }

    public static SensorState calibrating() {
        return new SensorState(null, "Sensor is calibrating. Check back soon.", Condition.CALIBRATING);
    }
}
