package com.hello.suripu.app.sensors.scales;

import com.google.common.collect.Lists;
import com.hello.suripu.app.sensors.Scale;
import com.hello.suripu.app.sensors.ScaleInterval;
import com.hello.suripu.core.roomstate.Condition;

import java.util.List;

public class HumidityScale extends Scale {

    @Override
    public List<ScaleInterval> intervals() {
        final ScaleInterval interval1 = new ScaleInterval("Dry", 0, 20, Condition.ALERT);
        final ScaleInterval interval2 = new ScaleInterval("Somewhat dry", 21, 30, Condition.WARNING);
        final ScaleInterval interval3 = new ScaleInterval("Ideal", 31, 60, Condition.IDEAL);
        final ScaleInterval interval4 = new ScaleInterval("Somewhat humid", 61, 80, Condition.WARNING);
        final ScaleInterval interval5 = new ScaleInterval("Humid", 81, 100, Condition.ALERT);

        final List<ScaleInterval> intervals = Lists.newArrayList(
                interval1, interval2,interval3,interval4,interval5
        );
        return intervals;
    }
}
