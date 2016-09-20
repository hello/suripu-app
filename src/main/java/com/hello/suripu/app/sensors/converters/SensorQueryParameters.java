package com.hello.suripu.app.sensors.converters;

import com.hello.suripu.app.sensors.QueryScope;
import org.joda.time.DateTime;

public class SensorQueryParameters {

    private final DateTime start;
    private final DateTime end;
    private final Integer slotDuration;

    public SensorQueryParameters(DateTime start, DateTime end, Integer slotDuration) {
        this.start = start;
        this.end = end;
        this.slotDuration = slotDuration;
    }

    public DateTime start() {
        return start;
    }

    public DateTime end() {
        return end;
    }

    public Integer slotDuration() {
        return slotDuration;
    }

    public static SensorQueryParameters from(final QueryScope scope, final DateTime refUTC) {

        switch(scope) {
            case DAY_5_MINUTE:
                return new SensorQueryParameters(refUTC.minusHours(24), refUTC, 5);
            case LAST_3H_5_MINUTE:
                return new SensorQueryParameters(refUTC.minusHours(3), refUTC, 5);
            case WEEK_1_HOUR:
                return new SensorQueryParameters(refUTC.minusDays(7), refUTC, 60);
            default:
                throw new IllegalArgumentException("not supported");
        }

    }
}
