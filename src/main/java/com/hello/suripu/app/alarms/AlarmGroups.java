package com.hello.suripu.app.alarms;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hello.suripu.core.models.Alarm;
import com.hello.suripu.core.models.AlarmSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AlarmGroups {
    private final List<Alarm> expansions = new ArrayList<>();
    private final List<Alarm> voice = new ArrayList<>();
    private final List<Alarm> classic = new ArrayList<>();

    private AlarmGroups(
            List<Alarm> expansions,
            List<Alarm> voice,
            List<Alarm> classic) {
        this.expansions.addAll(expansions);
        this.voice.addAll(voice);
        this.classic.addAll(classic);
    }

    @JsonCreator
    public static AlarmGroups create(
            @JsonProperty("expansions") List<Alarm> expansions,
            @JsonProperty("voice") List<Alarm> voice,
            @JsonProperty("classic") List<Alarm> classic) {
        return new AlarmGroups(expansions, voice, classic);
    }

    @JsonProperty("expansions")
    public List<Alarm> expansions() {
        final List<Alarm> filtered = classic.stream()
            .filter(a -> !(a.expansions == null))
            .collect(Collectors.toList());

        filtered.addAll(expansions);
        return filtered;
    }

    @JsonProperty("voice")
    public List<Alarm> voice() {
        final List<Alarm> filtered = classic.stream()
                .filter(a -> AlarmSource.VOICE_SERVICE.equals(a.alarmSource))
                .collect(Collectors.toList());

        filtered.addAll(voice);
        return filtered;
    }

    @JsonProperty("classic")
    public List<Alarm> classic() {
        return classic.stream()
                .filter(a -> !AlarmSource.VOICE_SERVICE.equals(a.alarmSource) && (a.expansions == null) )
                .collect(Collectors.toList());
    }
}
