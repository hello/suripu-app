package is.hello.supichi.commandhandlers;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.hello.suripu.core.db.SleepStatsDAODynamoDB;
import com.hello.suripu.core.models.AggregateSleepStats;
import com.hello.suripu.core.models.TimelineResult;
import com.hello.suripu.core.util.DateTimeUtil;
import com.hello.suripu.core.util.TimelineUtils;
import com.hello.suripu.coredropwizard.timeline.TimelineProcessor;
import is.hello.supichi.commandhandlers.results.GenericResult;
import is.hello.supichi.db.SpeechCommandDAO;
import is.hello.supichi.models.AnnotatedTranscript;
import is.hello.supichi.models.HandlerResult;
import is.hello.supichi.models.HandlerType;
import is.hello.supichi.models.SpeechCommand;
import is.hello.supichi.models.VoiceRequest;
import is.hello.supichi.response.SupichiResponseType;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

import static is.hello.supichi.commandhandlers.ErrorText.NO_SLEEP_DATA;
import static is.hello.supichi.commandhandlers.ErrorText.NO_SLEEP_SUMMARY;
import static is.hello.supichi.commandhandlers.ErrorText.NO_TIMEZONE;
import static is.hello.supichi.models.SpeechCommand.SLEEP_SCORE;
import static is.hello.supichi.models.SpeechCommand.SLEEP_SUMMARY;

/**
 * Created by ksg on 6/17/16
 */
public class SleepSummaryHandler extends BaseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SleepSummaryHandler.class);

    private static final String SLEEP_SUMMARY_PATTERN = "(how|how's)\\s(is|was|much|do|many hours|long)?\\s?(did)?\\s?(my|i|the)\\s(sleep)";
    public static final String ERROR_NO_SLEEP_STATS = "Sorry, you have no sleep data for last night.";
    public static final String ERROR_NO_TIMEZONE = "Sorry, we're unable to retrieve your sleep score. Please set your timezone in the app.";

    private final SleepStatsDAODynamoDB sleepStatsDAO;
    private final TimelineProcessor timelineProcessor;

    public SleepSummaryHandler(final SpeechCommandDAO speechCommandDAO, final SleepStatsDAODynamoDB sleepStatsDAO,
                               final TimelineProcessor timelineProcessor) {
        super("sleep-summary", speechCommandDAO, getAvailableActions());
        this.sleepStatsDAO = sleepStatsDAO;
        this.timelineProcessor = timelineProcessor;
    }


    private static Map<String, SpeechCommand> getAvailableActions() {
        final Map<String, SpeechCommand> tempMap = Maps.newHashMap();
        tempMap.put("sleep score", SpeechCommand.SLEEP_SCORE);
        tempMap.put("what was my score", SpeechCommand.SLEEP_SCORE);
        tempMap.put("what's my score", SpeechCommand.SLEEP_SCORE);

        tempMap.put("sleep summary", SpeechCommand.SLEEP_SUMMARY);
        tempMap.put(SLEEP_SUMMARY_PATTERN, SpeechCommand.SLEEP_SUMMARY);
//        tempMap.put("how was my sleep", SpeechCommand.SLEEP_SUMMARY);
//        tempMap.put("how is my sleep", SpeechCommand.SLEEP_SUMMARY);
//        tempMap.put("how's my sleep", SpeechCommand.SLEEP_SUMMARY);
//        tempMap.put("how did i sleep", SpeechCommand.SLEEP_SUMMARY);
//        tempMap.put("how do i sleep", SpeechCommand.SLEEP_SUMMARY);
        return tempMap;
    }

    @Override
    public HandlerResult executeCommand(final AnnotatedTranscript annotatedTranscript, final VoiceRequest request) {
        final Optional<SpeechCommand> optionalCommand = getCommand(annotatedTranscript);

        final Long accountId = request.accountId;

        if (optionalCommand.isPresent()) {
            final SpeechCommand command = optionalCommand.get();
            switch (command) {
                case SLEEP_SCORE:
                    return getSleepScore(accountId, annotatedTranscript);
                case SLEEP_SUMMARY:
                    return getSleepSummary(accountId, annotatedTranscript);
            }
        }

        return new HandlerResult(HandlerType.SLEEP_SUMMARY, HandlerResult.EMPTY_COMMAND, GenericResult.failWithResponse(NO_SLEEP_DATA, ERROR_NO_SLEEP_STATS));
    }

    private HandlerResult getSleepSummary(final Long accountId, final AnnotatedTranscript annotatedTranscript) {
        if (!annotatedTranscript.timeZoneOptional.isPresent()) {
            LOGGER.error("error=no-sleep-summary reason=no-timezone account_id={}", accountId);
            return new HandlerResult(HandlerType.SLEEP_SUMMARY, SLEEP_SUMMARY.getValue(), GenericResult.failWithResponse(NO_TIMEZONE, ERROR_NO_TIMEZONE));
        }

        final DateTimeZone timezoneId = DateTimeZone.forTimeZone(annotatedTranscript.timeZoneOptional.get());
        final Optional<AggregateSleepStats> optionalSleepStat = getSleepStat(accountId, timezoneId);
        if (!optionalSleepStat.isPresent()) {
            return new HandlerResult(HandlerType.SLEEP_SUMMARY, SLEEP_SUMMARY.getValue(), GenericResult.failWithResponse(NO_SLEEP_SUMMARY, ERROR_NO_SLEEP_STATS));
        }

        final TimelineUtils timelineUtils = new TimelineUtils(UUID.randomUUID());
        final String summary = timelineUtils.generateMessage(optionalSleepStat.get().sleepStats, 0, 0);
        return new HandlerResult(HandlerType.SLEEP_SUMMARY, SLEEP_SUMMARY.getValue(), GenericResult.ok(summary.replace("**", "")));
    }


    private HandlerResult getSleepScore(final Long accountId, final AnnotatedTranscript annotatedTranscript) {


        if (!annotatedTranscript.timeZoneOptional.isPresent()) {
            LOGGER.error("error=no-sleep-score reason=no-timezone account_id={}", accountId);
            return new HandlerResult(HandlerType.SLEEP_SUMMARY, SLEEP_SCORE.getValue(), GenericResult.failWithResponse(NO_TIMEZONE, ERROR_NO_TIMEZONE));
        }

        final DateTimeZone timezoneId = DateTimeZone.forTimeZone(annotatedTranscript.timeZoneOptional.get());
        final Optional<AggregateSleepStats> optionalSleepStat = getSleepStat(accountId, timezoneId);
        if (!optionalSleepStat.isPresent()) {
            return new HandlerResult(HandlerType.SLEEP_SUMMARY, SLEEP_SCORE.getValue(), GenericResult.failWithResponse(NO_SLEEP_DATA, ERROR_NO_SLEEP_STATS));
        }

        final String response = String.format("Your sleep score was %d last night", optionalSleepStat.get().sleepScore);
        return new HandlerResult(HandlerType.SLEEP_SUMMARY, SLEEP_SCORE.getValue(), GenericResult.ok(response));
    }

    private Optional<AggregateSleepStats> getSleepStat(final Long accountId, final DateTimeZone timezoneId) {
        final DateTime localToday = DateTime.now(timezoneId).withTimeAtStartOfDay();
        final Integer localHour = DateTime.now(timezoneId).getHourOfDay();
        final String lastNightDate = DateTimeUtil.dateToYmdString(localToday.minusDays(1));
        final String localCurrentDate = DateTimeUtil.dateToYmdString(localToday);

        LOGGER.debug("action=get-sleep-stats-from-ddb account_id={} night_date={}", accountId, lastNightDate);
        final Optional<AggregateSleepStats> optionalSleepStat = sleepStatsDAO.getSingleStat(accountId, lastNightDate);

        if (optionalSleepStat.isPresent()) {
            final AggregateSleepStats stat = optionalSleepStat.get();
            LOGGER.info("action=found-sleep-stats-in-ddb account_id={} night_date={} score={} sleep={} wake={} deep={}",
                    accountId, lastNightDate, stat.sleepScore,
                    stat.sleepStats.sleepTime, stat.sleepStats.wakeTime,
                    stat.sleepStats.soundSleepDurationInMinutes);
            return optionalSleepStat;
        }

        final DateTime targetDate = DateTimeUtil.ymdStringToDateTime(lastNightDate);
        final DateTime queryDate = DateTimeUtil.ymdStringToDateTime(localCurrentDate);
        LOGGER.debug("action=compute-timeline-for-stats account_id={} target_date={}", accountId, targetDate);

        final TimelineProcessor newTimelineProcessor = timelineProcessor.copyMeWithNewUUID(UUID.randomUUID());

        final TimelineResult result = newTimelineProcessor.retrieveTimelinesFast(accountId, targetDate,Optional.absent(), Optional.absent());

        if (!result.timelines.isEmpty() && result.timelines.get(0).score > 0 && result.timelines.get(0).statistics.isPresent()) {
            final AggregateSleepStats aggStats = new AggregateSleepStats.Builder()
                    .withSleepStats(result.timelines.get(0).statistics.get())
                    .withSleepScore(result.timelines.get(0).score).build();

            LOGGER.info("action=compute-timeline-ok account_id={} target_date={} score={} sleep={} wake={} deep={}",
                    accountId, targetDate, aggStats.sleepScore,
                    aggStats.sleepStats.sleepTime, aggStats.sleepStats.wakeTime,
                    aggStats.sleepStats.soundSleepDurationInMinutes);

            return Optional.of(aggStats);
        }

        LOGGER.info("action=fail-to-get-sleep-stats account_id={} target_date={}", accountId, targetDate);
        return Optional.absent();
    }

    @Override
    public SupichiResponseType responseType() {
        return SupichiResponseType.WATSON;
    }

}