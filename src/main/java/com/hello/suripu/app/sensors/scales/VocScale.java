package com.hello.suripu.app.sensors.scales;

import com.google.common.collect.Lists;
import com.hello.suripu.app.sensors.Scale;
import com.hello.suripu.app.sensors.ScaleInterval;
import com.hello.suripu.core.roomstate.Condition;

import java.util.List;

public class VocScale extends Scale {

    @Override
    public List<ScaleInterval> intervals() {

        return Lists.newArrayList(
                new ScaleInterval("Ideal", 0, 500, Condition.IDEAL),
                new ScaleInterval("Elevated", 500, 4000, Condition.WARNING),
                new ScaleInterval("Unhealthy", 4000, null, Condition.ALERT)
        );

    }
}
