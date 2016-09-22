package com.hello.suripu.app.sensors.scales;

import com.google.common.collect.Lists;
import com.hello.suripu.app.sensors.Scale;
import com.hello.suripu.app.sensors.ScaleInterval;
import com.hello.suripu.core.roomstate.Condition;

import java.util.List;

public class UvScale extends Scale {

    @Override
    public List<ScaleInterval> intervals() {

        return Lists.newArrayList(
                new ScaleInterval("Ideal", 0, 3, Condition.IDEAL),
                new ScaleInterval("Moderate", 3, 6, Condition.IDEAL),
                new ScaleInterval("High", 6, 8, Condition.WARNING),
                new ScaleInterval("Very High", 8, 11, Condition.WARNING),
                new ScaleInterval("Extreme", 11, null, Condition.ALERT)
        );
    }
}
