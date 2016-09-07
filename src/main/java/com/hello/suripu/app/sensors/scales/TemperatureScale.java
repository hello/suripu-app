package com.hello.suripu.app.sensors.scales;

import com.google.common.collect.Lists;
import com.hello.suripu.app.sensors.Scale;
import com.hello.suripu.app.sensors.ScaleInterval;
import com.hello.suripu.core.models.CurrentRoomState;

import java.util.List;

public class TemperatureScale implements Scale {
    @Override
    public List<ScaleInterval> intervals() {
        final ScaleInterval interval1 = new ScaleInterval("terrible", 0, 5, CurrentRoomState.State.Condition.ALERT);
        final ScaleInterval interval2 = new ScaleInterval("bad", 5, 10, CurrentRoomState.State.Condition.WARNING);
        final ScaleInterval interval3 = new ScaleInterval("ok", 10, 15, CurrentRoomState.State.Condition.IDEAL);
        final ScaleInterval interval4 = new ScaleInterval("bad too", 15, 20, CurrentRoomState.State.Condition.WARNING);
        final ScaleInterval interval5 = new ScaleInterval("terrible too", 15, 20, CurrentRoomState.State.Condition.ALERT);

        final List<ScaleInterval> intervals = Lists.newArrayList(
                interval1, interval2,interval3,interval4,interval5
        );
        return intervals;
    }
}
