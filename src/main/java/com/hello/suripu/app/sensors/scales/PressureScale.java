package com.hello.suripu.app.sensors.scales;

import com.hello.suripu.app.sensors.Scale;
import com.hello.suripu.app.sensors.ScaleInterval;
import com.hello.suripu.core.roomstate.Condition;

import java.util.ArrayList;
import java.util.List;

public class PressureScale extends Scale {

    private final float pressure;

    public PressureScale(float currentPressure) {
        this.pressure = currentPressure;
    }

    private static List<ScaleInterval> intervals = new ArrayList<>();
    static {
        intervals.add(new ScaleInterval("Decreasing", "The barometric pressure is decreasing.", null, -40.1f, Condition.ALERT));
        intervals.add(new ScaleInterval("Decreasing slightly", "The barometric pressure is decreasing.", -40f, -20.1f, Condition.WARNING));
        intervals.add(new ScaleInterval("Stable ", "The barometric pressure is just right.", -20f, 20f, Condition.IDEAL));
        intervals.add(new ScaleInterval("Increasing slightly", "The barometric pressure is increasing.", 20.1f, 40f, Condition.WARNING));
        intervals.add(new ScaleInterval("Increasing", "The barometric pressure is increasing.", 40.1f, null, Condition.ALERT));
    }

    @Override
    public List<ScaleInterval> intervals() {
        final List<ScaleInterval> calibratedIntervals = new ArrayList<>();
        for (final ScaleInterval interval : intervals) {
            calibratedIntervals.add(PressureScale.calibrate(interval, pressure));
        }
        return calibratedIntervals;
    }

    public Scale changeScale() {
        return new Scale() {
            @Override
            public List<ScaleInterval> intervals() {
                return intervals;
            }
        };
    }

    private static ScaleInterval calibrate(final ScaleInterval interval, final float value) {
        final Float min = interval.min() == null ? null : value + interval.min();
        final Float max = interval.max() == null ? null : value + interval.max();
        return new ScaleInterval(interval.name(),interval.message(),min, max, interval.condition());
    }
}
