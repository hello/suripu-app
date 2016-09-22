package com.hello.suripu.app.sensors.scales;

import com.google.common.collect.Lists;
import com.hello.suripu.app.sensors.Scale;
import com.hello.suripu.app.sensors.ScaleInterval;
import com.hello.suripu.core.roomstate.Condition;

import java.util.List;

public class Co2Scale extends Scale {

    @Override
    public List<ScaleInterval> intervals() {

        return Lists.newArrayList(
                new ScaleInterval("Ideal", 0, 600, Condition.IDEAL),
                new ScaleInterval("Elevated", 600, 1200, Condition.WARNING),
                new ScaleInterval("Unhealthy", 1200, null, Condition.ALERT)
        );

    }
}
