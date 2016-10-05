package com.hello.suripu.app.sensors.scales;

import com.hello.suripu.app.sensors.Scale;
import com.hello.suripu.app.sensors.ScaleInterval;
import com.hello.suripu.core.roomstate.Condition;

import java.util.ArrayList;
import java.util.List;

public class NoiseScale  extends Scale{

    private static List<ScaleInterval> intervals = new ArrayList<>();
    static {
        intervals.add(new ScaleInterval("Quiet", "The noise level is just right.", 0f, 64.9f, Condition.IDEAL));
        intervals.add(new ScaleInterval("Somewhat noisy", "It’s a bit noisy.", 65f, 69.9f, Condition.WARNING));
        intervals.add(new ScaleInterval("Noisy", "It’s a bit noisy.", 70f, 89.9f, Condition.WARNING));
        intervals.add(new ScaleInterval("Very noisy", "It’s far too noisy.", 90f, 129.9f, Condition.ALERT));
        intervals.add(new ScaleInterval("Extremely noisy", "It’s far too noisy.", 130f, null, Condition.ALERT));
    }

    @Override
    public List<ScaleInterval> intervals() {
        return intervals;
    }
}
