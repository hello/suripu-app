package is.hello.speech.models;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import is.hello.speech.models.annotations.DurationAnnotation;
import is.hello.speech.models.annotations.SleepSoundAnnotation;
import is.hello.speech.models.annotations.TimeAnnotation;
import is.hello.speech.models.annotations.VolumeAnnotation;

import java.util.List;
import java.util.TimeZone;

/**
 * Created by ksg on 9/20/16
 */
public class AnnotatedTranscript {

    public final String transcript;

    public final Optional<TimeZone> timeZoneOptional;

    public final List<TimeAnnotation> times;

    public final List<DurationAnnotation> durations;

    public final List<SleepSoundAnnotation> sleepSounds;

    public final List<VolumeAnnotation> volumes;

    // future entities
    // temperature
    // Hue patterns
    // Nest patterns
    // location
    // house location

    public AnnotatedTranscript(final String transcript, final Optional<TimeZone> timeZoneOptional,
                               final List<TimeAnnotation> times,
                               final List<DurationAnnotation> durations,
                               final List<SleepSoundAnnotation> sleepSounds,
                               final List<VolumeAnnotation> volumes) {
        this.transcript = transcript;
        this.timeZoneOptional = timeZoneOptional;
        this.times = times;
        this.durations = durations;
        this.sleepSounds = sleepSounds;
        this.volumes = volumes;
    }

    public static class Builder {
        private String transcript = "";
        private Optional<TimeZone> timeZoneOptional = Optional.absent();
        private List<TimeAnnotation> times = Lists.newArrayList();
        private List<DurationAnnotation> durations = Lists.newArrayList();
        private List<SleepSoundAnnotation> sleepSounds = Lists.newArrayList();
        private List<VolumeAnnotation> volumes = Lists.newArrayList();

        public Builder withTranscript(final String text) {
            this.transcript = text;
            return this;
        }

        public Builder withTimeZone(final Optional<TimeZone> timeZone) {
            this.timeZoneOptional = timeZone;
            return this;
        }

        public Builder withTimes(final List<TimeAnnotation> times) {
            this.times.addAll(times);
            return this;
        }

        public Builder withDurations(final List<DurationAnnotation> durations) {
            this.durations.addAll(durations);
            return this;
        }

        public Builder withSleepSounds(final List<SleepSoundAnnotation> sounds) {
            this.sleepSounds.addAll(sounds);
            return this;
        }

        public Builder withVolumes(final List<VolumeAnnotation> volumes) {
            this.volumes.addAll(volumes);
            return this;
        }

        public AnnotatedTranscript build() {
            return new AnnotatedTranscript(transcript, timeZoneOptional, times, durations, sleepSounds, volumes);
        }
    }
}
