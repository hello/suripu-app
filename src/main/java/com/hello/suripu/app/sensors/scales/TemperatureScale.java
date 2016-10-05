package com.hello.suripu.app.sensors.scales;

import com.hello.suripu.app.sensors.Scale;
import com.hello.suripu.app.sensors.ScaleInterval;
import com.hello.suripu.core.roomstate.Condition;

import java.util.ArrayList;
import java.util.List;

public class TemperatureScale extends Scale {

    private static List<ScaleInterval> intervals = new ArrayList<>();
    static {
        intervals.add(new ScaleInterval("Cold", "It’s far too cold.", null, 9.9f, Condition.ALERT));
        intervals.add(new ScaleInterval("Cool", "It’s a bit cool.", 10f, 14.9f, Condition.WARNING));
        intervals.add(new ScaleInterval("Ideal", "The temperature is just right.", 15f, 19.9f, Condition.IDEAL));
        intervals.add(new ScaleInterval("Warm", "It’s a bit warm.", 20f, 25.9f, Condition.WARNING));
        intervals.add(new ScaleInterval("Hot", "It’s far too hot.", 26f, null, Condition.ALERT));
    }

    @Override
    public List<ScaleInterval> intervals() {
        return intervals;
    }
}
