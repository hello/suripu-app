package is.hello.supichi.models;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;
import is.hello.supichi.commandhandlers.SleepSoundHandler;
import is.hello.supichi.models.annotations.DurationAnnotation;
import is.hello.supichi.models.annotations.SleepSoundAnnotation;
import is.hello.supichi.models.annotations.TimeAnnotation;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * Created by ksg on 9/20/16
 */
public class Annotator {
    private final static Logger LOGGER = LoggerFactory.getLogger(Annotator.class);

    private static class TimeDurations {
        public final List<TimeAnnotation> times;
        public final List<DurationAnnotation> durations;

        public TimeDurations(final List<TimeAnnotation> times, final List<DurationAnnotation> durations) {
            this.times = times;
            this.durations = durations;
        }
    }

    public static AnnotatedTranscript get(final String text, final Optional<TimeZone> timezone) {

        final AnnotatedTranscript.Builder builder = new AnnotatedTranscript.Builder()
                .withTranscript(text)
                .withSleepSounds(getSleepSounds(text.toLowerCase()));

        if (timezone.isPresent()) {
            final TimeDurations timeDurations = getTimeDurations(text.toLowerCase(), timezone.get());
            builder.withTimeZone(timezone)
                    .withTimes(timeDurations.times)
                    .withDurations(timeDurations.durations);
        }

        return builder.build();
    }

    private static List<SleepSoundAnnotation> getSleepSounds(final String text) {
        final List<SleepSoundAnnotation> entities = Lists.newArrayList();
        for (final SleepSoundHandler.SoundName soundName : SleepSoundHandler.SoundName.values()) {
            if (text.contains(soundName.value.toLowerCase())) {
                entities.add(new SleepSoundAnnotation(soundName.value, soundName));
            }
        }

        // map similar-sounding words to correct sound-names
        if (entities.isEmpty()) {
            if (text.contains("play fireplace") || text.contains("play campfire")) {
                entities.add(new SleepSoundAnnotation(SleepSoundHandler.SoundName.FIRESIDE.value, SleepSoundHandler.SoundName.FIRESIDE));
            } else if (text.contains("play raindrops") || text.contains("play rain")) {
                entities.add(new SleepSoundAnnotation(SleepSoundHandler.SoundName.RAINFALL.value, SleepSoundHandler.SoundName.RAINFALL));
            }  else if (text.contains("aurora")) {
                entities.add(new SleepSoundAnnotation(SleepSoundHandler.SoundName.AURA.value, SleepSoundHandler.SoundName.AURA));
            }
        }

        return entities;
    }

    private static TimeDurations getTimeDurations(final String text, final TimeZone timezone) {
        // final Parser timeParser = new Parser(DateTimeZone.forID("America/Los_Angeles").toTimeZone());
        final Parser timeParser = new Parser(timezone);
        final List<TimeAnnotation> times = Lists.newArrayList();
        final List<DurationAnnotation> durations = Lists.newArrayList();

        final List<DateGroup> groups = timeParser.parse(text);
        for (final DateGroup group : groups) {
            final List<Date> dates = group.getDates();
            if (dates.size() == 2) {
                // duration
                final DateTime endTime = new DateTime(dates.get(1).getTime());
                final org.joda.time.Duration duration = new org.joda.time.Duration(new DateTime(dates.get(0).getTime()), endTime);
                durations.add(new DurationAnnotation(group.getText(), duration));
                times.add(new TimeAnnotation(group.getText(), endTime));
            } else {
                for (final Date date : dates) {
                    final String matchingValue = group.getText();
                    final DateTime dateTime = new DateTime(date.getTime()); // local utc ts
                    LOGGER.debug("action=parse_time text={} date={} matched_string={} datetime={}",
                            text, date, matchingValue, dateTime);
                    times.add(new TimeAnnotation(matchingValue, dateTime));
                }
            }
        }

        return new TimeDurations(times, durations);
    }
}
