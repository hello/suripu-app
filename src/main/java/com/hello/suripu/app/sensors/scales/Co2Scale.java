package com.hello.suripu.app.sensors.scales;

import com.hello.suripu.app.sensors.Scale;
import com.hello.suripu.app.sensors.ScaleInterval;
import com.hello.suripu.core.roomstate.Condition;

import java.util.ArrayList;
import java.util.List;

public class Co2Scale extends Scale {

    private static List<ScaleInterval> intervals = new ArrayList<>();
    static {
        intervals.add(new ScaleInterval("Ideal", "The CO2 level is just right.", 0f, 599.9f, Condition.IDEAL));
        intervals.add(new ScaleInterval("A bit stuffy", "The air is a bit stuffy.", 600f, 1199.9f, Condition.WARNING));
        intervals.add(new ScaleInterval("Stuffy", "The air is stuffy.", 1200f, null, Condition.ALERT));
    }
    @Override
    public List<ScaleInterval> intervals() {
        return intervals;
    }
}
