package com.hello.suripu.app.sensors.scales;

import com.google.common.collect.Lists;
import com.hello.suripu.app.sensors.Scale;
import com.hello.suripu.app.sensors.ScaleInterval;
import com.hello.suripu.core.roomstate.Condition;

import java.util.List;

public class TemperatureScale implements Scale {
    @Override
    public List<ScaleInterval> intervals() {
        final ScaleInterval interval1 = new ScaleInterval("Cold", null, 10, Condition.ALERT);
        final ScaleInterval interval2 = new ScaleInterval("Cool", 11, 14, Condition.WARNING);
        final ScaleInterval interval3 = new ScaleInterval("Ideal", 15, 20, Condition.IDEAL);
        final ScaleInterval interval4 = new ScaleInterval("Warm", 21, 25, Condition.WARNING);
        final ScaleInterval interval5 = new ScaleInterval("Hot", 26, null, Condition.ALERT);

        final List<ScaleInterval> intervals = Lists.newArrayList(
                interval1, interval2,interval3,interval4,interval5
        );
        return intervals;
    }
}
