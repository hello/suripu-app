package com.hello.suripu.app.sensors.scales;

import com.hello.suripu.app.sensors.Scale;
import com.hello.suripu.app.sensors.ScaleInterval;
import com.hello.suripu.core.roomstate.Condition;

import java.util.ArrayList;
import java.util.List;

public class VocScale extends Scale {

    private static List<ScaleInterval> intervals = new ArrayList<>();
    static {
        intervals.add(new ScaleInterval("Ideal", "The VOC level is just right.", 0f, 499f, Condition.IDEAL));
        intervals.add(new ScaleInterval("Elevated", "The VOC level is elevated.", 500f, 3999.9f, Condition.WARNING));
        intervals.add(new ScaleInterval("Unhealthy", "The VOC level is unhealthy.", 4000f, null, Condition.ALERT));
    }
    @Override
    public List<ScaleInterval> intervals() {
        return intervals;
    }
}
