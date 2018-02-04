package com.hello.suripu.app.sensors.converters;

import com.hello.suripu.app.sensors.QueryScope;
import org.joda.time.DateTime;

public class SensorQueryParameters {

    private final DateTime start;
    private final DateTime end;
    private final Integer slotDuration;

    public SensorQueryParameters(final DateTime start, final DateTime end, final Integer slotDuration) {
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
        final DateTime end = refUTC.minusMinutes(3); // should trim the most recent minutes to avoid empty data
        switch(scope) {
            case DAY_5_MINUTE:
                return new SensorQueryParameters(refUTC.minusHours(24), end, 5);
            case LAST_3H_5_MINUTE:
                return new SensorQueryParameters(refUTC.minusHours(3), end, 5);
            case WEEK_1_HOUR:
                return new SensorQueryParameters(refUTC.minusDays(7), end, 60);
            default:
                throw new IllegalArgumentException("not supported");
        }

    }
}
