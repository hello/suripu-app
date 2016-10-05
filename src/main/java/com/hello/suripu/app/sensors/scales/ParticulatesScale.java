package com.hello.suripu.app.sensors.scales;

import com.hello.suripu.app.sensors.Scale;
import com.hello.suripu.app.sensors.ScaleInterval;
import com.hello.suripu.core.roomstate.Condition;

import java.util.ArrayList;
import java.util.List;

public class ParticulatesScale extends Scale {
    private static List<ScaleInterval> intervals = new ArrayList<>();
    static {
        intervals.add(new ScaleInterval("Ideal", "The air quality is just right.", 0f, 49.9f, Condition.IDEAL));
        intervals.add(new ScaleInterval("Moderate", "The air quality is moderate.", 50f, 99.9f, Condition.IDEAL));
        intervals.add(new ScaleInterval("Unhealthy for sensitive groups", "The air quality is unhealthy for sensitive groups.", 100f, 149.9f, Condition.WARNING));
        intervals.add(new ScaleInterval("Unhealthy", "The air quality is unhealthy.", 150f, 199.9f, Condition.WARNING));
        intervals.add(new ScaleInterval("Very unhealthy", "The air quality is very unhealthy.", 200f, 299.9f, Condition.ALERT));
        intervals.add(new ScaleInterval("Hazardous", "The air quality is hazardous.", 300f, 399.9f, Condition.ALERT));
    }

    @Override
    public List<ScaleInterval> intervals() {
        return intervals;
    }
}
