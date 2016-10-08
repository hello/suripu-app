package com.hello.suripu.app.sensors.scales;

import com.hello.suripu.app.sensors.Scale;
import com.hello.suripu.app.sensors.ScaleInterval;
import com.hello.suripu.core.roomstate.Condition;

import java.util.ArrayList;
import java.util.List;

public class UvScale extends Scale {

    private static List<ScaleInterval> intervals = new ArrayList<>();
    static {
        intervals.add(new ScaleInterval("Low", "The UV level is just right.", 0f, 2.99f, Condition.IDEAL));
        intervals.add(new ScaleInterval("Moderate", "The UV level is a bit high.", 3f, 5.99f, Condition.WARNING));
        intervals.add(new ScaleInterval("High", "The UV level is far too high.", 6f, 7.99f, Condition.ALERT));
        intervals.add(new ScaleInterval("Very High", "The UV level is far too high.", 8f, 10.99f, Condition.ALERT));
        intervals.add(new ScaleInterval("Extreme", "The UV level is far too high.", 11f, null, Condition.ALERT));
    }
    @Override
    public List<ScaleInterval> intervals() {
        return intervals;
    }
}
