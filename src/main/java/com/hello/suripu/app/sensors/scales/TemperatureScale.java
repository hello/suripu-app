package com.hello.suripu.app.sensors.scales;

import com.hello.suripu.app.sensors.Scale;
import com.hello.suripu.app.sensors.ScaleInterval;
import com.hello.suripu.core.roomstate.Condition;

import java.util.ArrayList;
import java.util.List;

public class TemperatureScale extends Scale {

    private static List<ScaleInterval> intervals = new ArrayList<>();
    static {
        intervals.add(new ScaleInterval("Cold", null, 10, Condition.ALERT));
        intervals.add(new ScaleInterval("Cool", 11, 14, Condition.WARNING));
        intervals.add(new ScaleInterval("Ideal", 15, 20, Condition.IDEAL));
        intervals.add(new ScaleInterval("Warm", 21, 25, Condition.WARNING));
        intervals.add(new ScaleInterval("Hot", 26, null, Condition.ALERT));
    }

    @Override
    public List<ScaleInterval> intervals() {
        return intervals;
    }
}
