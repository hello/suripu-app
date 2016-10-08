package com.hello.suripu.app.sensors.scales;

import com.hello.suripu.app.sensors.Scale;
import com.hello.suripu.app.sensors.ScaleInterval;
import com.hello.suripu.core.roomstate.Condition;

import java.util.ArrayList;
import java.util.List;

public class LightScale extends Scale {

    private static List<ScaleInterval> intervals = new ArrayList<>();
    static {
        intervals.add(new ScaleInterval("Ideal", "The light level is just right.", 0f, 1.99f, Condition.IDEAL));
        intervals.add(new ScaleInterval("Somewhat bright", "It’s a bit bright.", 2f, 7.99f, Condition.WARNING));
        intervals.add(new ScaleInterval("Bright", "It’s a bit bright.", 8f, 14.99f, Condition.ALERT));
        intervals.add(new ScaleInterval("Very bright", "It’s far too bright.", 15f, 49.99f, Condition.ALERT));
        intervals.add(new ScaleInterval("Extremely bright", "It’s far too bright.", 50f, null, Condition.ALERT));
    }
    @Override
    public List<ScaleInterval> intervals() {
        return intervals;
    }
}
