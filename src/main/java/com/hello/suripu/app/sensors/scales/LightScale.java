package com.hello.suripu.app.sensors.scales;

import com.google.common.collect.Lists;
import com.hello.suripu.app.sensors.Scale;
import com.hello.suripu.app.sensors.ScaleInterval;
import com.hello.suripu.core.roomstate.Condition;

import java.util.List;

public class LightScale implements Scale {
    @Override
    public List<ScaleInterval> intervals() {
        final ScaleInterval interval1 = new ScaleInterval("Ideal", 0, 2, Condition.IDEAL);
        final ScaleInterval interval2 = new ScaleInterval("Somewhat bright", 3, 8, Condition.WARNING);
        final ScaleInterval interval3 = new ScaleInterval("Bright", 9, 15, Condition.ALERT);
        final ScaleInterval interval4 = new ScaleInterval("Very bright", 16, 50, Condition.ALERT);
        final ScaleInterval interval5 = new ScaleInterval("Extremely bright", 51, null, Condition.ALERT);

        final List<ScaleInterval> intervals = Lists.newArrayList(
                interval1, interval2,interval3,interval4,interval5
        );
        return intervals;
    }
}
