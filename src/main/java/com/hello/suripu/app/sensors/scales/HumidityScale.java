package com.hello.suripu.app.sensors.scales;

import com.hello.suripu.app.sensors.Scale;
import com.hello.suripu.app.sensors.ScaleInterval;
import com.hello.suripu.core.roomstate.Condition;

import java.util.ArrayList;
import java.util.List;

public class HumidityScale extends Scale {

    private static List<ScaleInterval> intervals = new ArrayList<>();
    static {
        intervals.add(new ScaleInterval("Dry", "It’s far too dry.", 0f, 20f, Condition.ALERT));
        intervals.add(new ScaleInterval("Somewhat dry", "It’s a bit dry.", 21f, 30f, Condition.WARNING));
        intervals.add(new ScaleInterval("Ideal", "The humidity is just right.", 31f, 60f, Condition.IDEAL));
        intervals.add(new ScaleInterval("Somewhat humid", "It’s a bit humid.", 61f, 80f, Condition.WARNING));
        intervals.add(new ScaleInterval("Humid", "It’s far too humid.", 81f, 100f, Condition.ALERT));
    }
    @Override
    public List<ScaleInterval> intervals() {
        return intervals;
    }
}
