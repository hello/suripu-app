package is.hello.supichi.commandhandlers;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hello.suripu.core.alarm.AlarmProcessor;
import com.hello.suripu.core.db.MergedUserInfoDynamoDB;
import com.hello.suripu.core.models.Alarm;
import com.hello.suripu.core.models.AlarmSound;
import com.hello.suripu.core.models.AlarmSource;
import com.hello.suripu.core.models.RingTime;
import com.hello.suripu.core.models.UserInfo;
import com.hello.suripu.core.processors.RingProcessor;
import is.hello.supichi.commandhandlers.results.GenericResult;
import is.hello.supichi.db.SpeechCommandDAO;
import is.hello.supichi.models.AnnotatedTranscript;
import is.hello.supichi.models.HandlerResult;
import is.hello.supichi.models.HandlerType;
import is.hello.supichi.models.SpeechCommand;
import is.hello.supichi.models.VoiceRequest;
import is.hello.supichi.models.annotations.TimeAnnotation;
import is.hello.supichi.response.SupichiResponseType;
import jersey.repackaged.com.google.common.collect.Sets;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static is.hello.supichi.commandhandlers.ErrorText.DUPLICATE_ERROR;
import static is.hello.supichi.commandhandlers.ErrorText.ERROR_NO_ALARM_TO_CANCEL;
import static is.hello.supichi.commandhandlers.ErrorText.NO_ALARM_SET_ERROR;
import static is.hello.supichi.commandhandlers.ErrorText.NO_TIMEZONE;
import static is.hello.supichi.commandhandlers.ErrorText.NO_TIME_ERROR;
import static is.hello.supichi.commandhandlers.ErrorText.NO_USER_INFO;
import static is.hello.supichi.commandhandlers.ErrorText.SMART_ALARM_ERROR;
import static is.hello.supichi.commandhandlers.ErrorText.TOO_LATE_ERROR;
import static is.hello.supichi.commandhandlers.ErrorText.TOO_SOON_ERROR;
import static is.hello.supichi.models.HandlerResult.EMPTY_COMMAND;

/**
 * Created by ksg on 6/17/16
 */
public class AlarmHandler extends BaseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlarmHandler.class);

    private static final int MIN_ALARM_MINUTES_FROM_NOW = 5;
    private static final int MAX_ALARM_MINUTES_FROM_NOW = 1439; // 24 hours

    private static final String CANCEL_ALARM_REGEX = "(cancel|delete|remove|unset).*(?:alarm)(s?)";
    private static final Pattern CANCEL_ALARM_PATTERN = Pattern.compile(CANCEL_ALARM_REGEX);

    private static final String SET_ALARM_REGEX = "((set).*(?:alarm))|(wake me)";
    private static final Pattern SET_ALARM_PATTERN = Pattern.compile(SET_ALARM_REGEX);

    private static final String GET_ALARM_REGEX = "(what|when)('s)?(.+)?\\s(alarm)";
    private static final Pattern GET_ALARM_PATTERN = Pattern.compile(GET_ALARM_REGEX);

    public static final AlarmSound DEFAULT_ALARM_SOUND = new AlarmSound(5, "Dusk", "");

    // TODO: these responses should be moved to a dedicated AlarmResponseBuilder
    public static final String DUPLICATE_ALARM_RESPONSE = "Sorry, no alarm was set, you already have an alarm set for %s";
    public static final String SET_ALARM_ERROR_RESPONSE = "Sorry, your alarm could not be set. Please try again later";
    public static final String SET_ALARM_OK_RESPONSE = "Ok, your alarm is set for %s";

    public static final String SET_ALARM_ERROR_TOO_LATE_RESPONSE = "Your alarm could not be set. Please set a time no more than one day ahead.";
    public static final String SET_ALARM_ERROR_TOO_SOON_RESPONSE = "Your alarm could not be set. Please set a time greater than 5 minutes from now";
    public static final String SET_ALARM_ERROR_NO_TIME_RESPONSE = "Your alarm could not be set. Please specify an alarm time.";
    public static final String SET_ALARM_ERROR_NO_TIME_ZONE = "Your alarm could not be set. Please set your timezone in the mobile app.";

    public static final String CANCEL_ALARM_ERROR_RESPONSE = "Your alarm could not be cancelled. Please try again later.";
    public static final String CANCEL_ALARM_OK_RESPONSE_TEMPLATE = "OK, your alarm for %s is canceled.";
    public static final String NO_ALARM_RESPONSE = "There is no non-repeating alarm to cancel.";
    public static final String REPEATED_ALARM_CANCEL_INSTRUCTIONS = "You only have a repeating alarm set. Use the app to cancel.";
    public static final String SMART_ALARM_ERROR_RESPONSE = "Smart Alarm could not be set. Use the app to set a Smart Alarm.";

    public static final String NO_ALARM_TO_GET_RESPONSE = "You have no alarm set.";
    public static final String GET_ALARM_OK_RESPONSE_TEMPLATE = "Your next alarm is for %s.";

    public static final String SMART_ALARM_CHECK_STRING = "smart alarm";


    private final AlarmProcessor alarmProcessor;
    private final MergedUserInfoDynamoDB mergedUserInfoDynamoDB;

    public AlarmHandler(final SpeechCommandDAO speechCommandDAO, final AlarmProcessor alarmProcessor, final MergedUserInfoDynamoDB mergedUserInfoDynamoDB) {
        super("alarm", speechCommandDAO, getAvailableActions());
        this.alarmProcessor = alarmProcessor;
        this.mergedUserInfoDynamoDB = mergedUserInfoDynamoDB;
    }


    private static Map<String, SpeechCommand> getAvailableActions() {
        // TODO read from DynamoDB
        final Map<String, SpeechCommand> tempMap = Maps.newHashMap();

        tempMap.put(SET_ALARM_REGEX, SpeechCommand.ALARM_SET);
        tempMap.put("set smart alarm", SpeechCommand.ALARM_SET);
        tempMap.put("set alarm", SpeechCommand.ALARM_SET);
        tempMap.put("set an alarm", SpeechCommand.ALARM_SET);
        tempMap.put("wake me", SpeechCommand.ALARM_SET);
        tempMap.put("wake me up", SpeechCommand.ALARM_SET);

        tempMap.put(CANCEL_ALARM_REGEX, SpeechCommand.ALARM_DELETE);
        tempMap.put("cancel alarm", SpeechCommand.ALARM_DELETE);
        tempMap.put("unset alarm", SpeechCommand.ALARM_DELETE);
        tempMap.put("remove alarm", SpeechCommand.ALARM_DELETE);
        tempMap.put("delete alarm", SpeechCommand.ALARM_DELETE);

        tempMap.put(GET_ALARM_REGEX, SpeechCommand.ALARM_GET);
        return tempMap;
    }

    @Override
    public Optional<SpeechCommand> getCommand(final AnnotatedTranscript transcript) {
        final String text = transcript.lowercaseTranscript();
        final Matcher cancelMatcher = CANCEL_ALARM_PATTERN.matcher(text);
        if (cancelMatcher.find()) {
            return Optional.of (SpeechCommand.ALARM_DELETE);
        }

        final Matcher setMatcher = SET_ALARM_PATTERN.matcher(text);
        if (setMatcher.find()) {
            return Optional.of(SpeechCommand.ALARM_SET);
        }

        final Matcher getMatcher = GET_ALARM_PATTERN.matcher(text);
        if (getMatcher.find()) {
            return Optional.of(SpeechCommand.ALARM_GET);
        }
        return Optional.absent();
    }

    @Override
    public HandlerResult executeCommand(final AnnotatedTranscript annotatedTranscript, final VoiceRequest request) {
        final Optional<SpeechCommand> optionalCommand = getCommand(annotatedTranscript);

        final Long accountId = request.accountId;
        final String senseId = request.senseId;

        if (!optionalCommand.isPresent()) {
            return new HandlerResult(HandlerType.ALARM, EMPTY_COMMAND, GenericResult.fail("no alarm set"));
        }

        final String command = optionalCommand.get().getValue();

        final GenericResult alarmResult;
        switch (optionalCommand.get()) {
            case ALARM_SET:
                alarmResult = setAlarm(accountId, senseId, annotatedTranscript);
                break;
            case ALARM_GET:
                alarmResult = getAlarm(accountId, senseId, annotatedTranscript);
                break;
            default:
                alarmResult = cancelAlarm(accountId, senseId);
        }

        return new HandlerResult(HandlerType.ALARM, command, alarmResult);
    }

    /**
     * get next ring time
     */
    private GenericResult getAlarm(final Long accountId, final String senseId, final AnnotatedTranscript annotatedTranscript) {
        final Optional<UserInfo> alarmInfoOptional = this.mergedUserInfoDynamoDB.getInfo(senseId, accountId);
        if (!alarmInfoOptional.isPresent()) {
            LOGGER.warn("warning=no-user-info sense_id={} account_id={}", senseId, accountId);
            return GenericResult.failWithResponse(NO_USER_INFO, CANCEL_ALARM_ERROR_RESPONSE);
        }

        final UserInfo userInfo = alarmInfoOptional.get();
        if (userInfo.alarmList.isEmpty()) {
            LOGGER.warn("warning=no-alarms-set sense_id={} account_id={}", senseId, accountId);
            return GenericResult.failWithResponse(NO_ALARM_SET_ERROR, NO_ALARM_TO_GET_RESPONSE);
        }

        final RingTime nextRingTime = RingProcessor.getNextRingTimeForSense(senseId, Lists.newArrayList(userInfo), DateTime.now());

        final DateTimeZone timezoneId = DateTimeZone.forTimeZone(annotatedTranscript.timeZoneOptional.get());
        final DateTime localRingTime = new DateTime(nextRingTime.actualRingTimeUTC, DateTimeZone.UTC).withZone(timezoneId);
        final DateTime localNow = DateTime.now(DateTimeZone.UTC).withZone(userInfo.timeZone.get());

        if (localRingTime.isBefore(localNow)) {
            LOGGER.warn("warning=no-future-alarms-set sense_id={} account_id={}", senseId, accountId);
            return GenericResult.failWithResponse(NO_ALARM_SET_ERROR, NO_ALARM_TO_GET_RESPONSE);
        }

        final String alarmString;
        if (localRingTime.getDayOfYear() ==  localNow.getDayOfYear()) {
            alarmString = String.format("%s today", localRingTime.toString(DateTimeFormat.forPattern("hh:mm a")));
        } else if ( localRingTime.getDayOfYear() == localNow.plusDays(1).getDayOfYear()) {
            alarmString = String.format("%s tomorrow", localRingTime.toString(DateTimeFormat.forPattern("hh:mm a")));
        } else {

            final String descriptor = (localRingTime.getWeekOfWeekyear() == localNow.getWeekOfWeekyear()) ? "this" : "next";
            alarmString =  String.format("%s %s at %s", descriptor,
                    localRingTime.toString("EEEE"),
                    localRingTime.toString("hh:mm a"));
        }

        return GenericResult.ok(String.format(GET_ALARM_OK_RESPONSE_TEMPLATE, alarmString));
    }

    /**
     * set alarm for the next matching time
     */
    private GenericResult setAlarm(final Long accountId, final String senseId, final AnnotatedTranscript annotatedTranscript) {
        if (!annotatedTranscript.timeZoneOptional.isPresent()) {
            LOGGER.error("error=no-alarm-set reason=no-timezone account_id={}", accountId);
            return GenericResult.failWithResponse(NO_TIMEZONE, SET_ALARM_ERROR_NO_TIME_ZONE);
        }

        if (annotatedTranscript.times.isEmpty()) {
            LOGGER.error("error=no-alarm-set reason=no-time-given text={} account_id={}", annotatedTranscript.lowercaseTranscript(), accountId);
            return GenericResult.failWithResponse(NO_TIME_ERROR, SET_ALARM_ERROR_NO_TIME_RESPONSE);
        }

        if (annotatedTranscript.lowercaseTranscript().contains(SMART_ALARM_CHECK_STRING)) {
            LOGGER.error("error=tried-to-set-smart-alarm text={} account_id={}", annotatedTranscript.lowercaseTranscript(), accountId);
            return GenericResult.failWithResponse(SMART_ALARM_ERROR, SMART_ALARM_ERROR_RESPONSE);
        }


        final TimeAnnotation timeAnnotation = annotatedTranscript.times.get(0); // note time is in utc, need to convert
        final DateTimeZone timezoneId = DateTimeZone.forTimeZone(annotatedTranscript.timeZoneOptional.get());
        final DateTime annotatedTimeUTC = new DateTime(timeAnnotation.dateTime(), DateTimeZone.UTC);
        final DateTime now = DateTime.now(DateTimeZone.UTC);

        final DateTime alarmTimeLocal =  (annotatedTimeUTC.isAfter(now)) ? annotatedTimeUTC.withZone(timezoneId) : annotatedTimeUTC.plusDays(1).withZone(timezoneId);
        final DateTime localNow = now.withZone(timezoneId);

        LOGGER.debug("action=create-alarm-time account_id={} annotation_time_utc={} now_utc={} local_alarm_time={} local_now={}",
                accountId, annotatedTimeUTC.toString(), now, alarmTimeLocal.toString(), localNow.toString());

        // check alarm time is more than 5 minutes from localNow
        final DateTime alarmTimeToMinute = alarmTimeLocal.withSecondOfMinute(0).withMillisOfSecond(0);
        if (alarmTimeToMinute.isBefore(localNow.plusMinutes(MIN_ALARM_MINUTES_FROM_NOW))) {
            LOGGER.error("error=alarm-time-too-soon local_now={} alarm_now={}", localNow, alarmTimeLocal);
            return GenericResult.failWithResponse(TOO_SOON_ERROR, SET_ALARM_ERROR_TOO_SOON_RESPONSE);
        }

        // check that alarm is no more than 24 hours from now
        if (alarmTimeToMinute.isAfter(localNow.plusMinutes(MAX_ALARM_MINUTES_FROM_NOW))) {
            LOGGER.error("error=alarm-time-too-late local_now={} alarm_now={}", localNow, alarmTimeLocal);
            return GenericResult.failWithResponse(TOO_LATE_ERROR, SET_ALARM_ERROR_TOO_LATE_RESPONSE);
        }

        final String newAlarmString;
        if (alarmTimeLocal.getDayOfYear() ==  localNow.getDayOfYear()) {
            newAlarmString = String.format("%s today", alarmTimeLocal.toString(DateTimeFormat.forPattern("hh:mm a")));
        } else if ( alarmTimeLocal.getDayOfYear() == localNow.plusDays(1).getDayOfYear()) {
            newAlarmString = String.format("%s tomorrow", alarmTimeLocal.toString(DateTimeFormat.forPattern("hh:mm a")));
        } else {
            newAlarmString = timeAnnotation.matchingText();
        }


        final Alarm newAlarm = new Alarm.Builder()
                .withYear(alarmTimeLocal.getYear())
                .withMonth(alarmTimeLocal.getMonthOfYear())
                .withDay(alarmTimeLocal.getDayOfMonth())
                .withHour(alarmTimeLocal.getHourOfDay())
                .withMinute(alarmTimeLocal.getMinuteOfHour())
                .withDayOfWeek(Sets.newHashSet())
                .withIsRepeated(false)
                .withAlarmSound(DEFAULT_ALARM_SOUND)
                .withIsEnabled(true)
                .withIsEditable(true)
                .withIsSmart(false)
                .withSource(AlarmSource.VOICE_SERVICE)
                .withId(UUID.randomUUID().toString().toUpperCase())
                .build();

        final List<Alarm> currentAlarms = alarmProcessor.getAlarms(accountId, senseId);
        final List<Alarm> newAlarms = Lists.newArrayList();

        final RingTime newAlarmRingTime = Alarm.Utils.generateNextRingTimeFromAlarmTemplatesForUser(
                Collections.singletonList(newAlarm), now.getMillis(), timezoneId);


        // check if we can add this alarm
        for (final Alarm alarm : currentAlarms) {

            // check that alarm is not a duplicate
            if (alarm.isEnabled) {
                final RingTime alarmRingTime = Alarm.Utils.generateNextRingTimeFromAlarmTemplatesForUser(
                        Collections.singletonList(alarm), now.getMillis(), timezoneId);
                if (alarmRingTime.expectedRingTimeUTC == newAlarmRingTime.expectedRingTimeUTC) {
                    // duplicate alarm
                    LOGGER.error("error=no-alarm-set reason=duplicate-alarm alarm={} account_id={}", newAlarm.toString());
                    return GenericResult.failWithResponse(DUPLICATE_ERROR, String.format(DUPLICATE_ALARM_RESPONSE, newAlarmString));
                }
            }

            // remove old voice alarm
            if (alarm.alarmSource.equals(AlarmSource.VOICE_SERVICE)) {
                final DateTime ringTime = new DateTime(alarm.year, alarm.month, alarm.day, alarm.hourOfDay, alarm.minuteOfHour, 0, timezoneId);
                if (ringTime.isBefore(localNow)) {
                    continue;
                }
            }
            newAlarms.add(alarm);
        }

        final int alarmsRemoved = currentAlarms.size() - newAlarms.size();
        LOGGER.debug("alarms_removed={} account_id={}", alarmsRemoved, accountId);

        // okay to set alarm
        try {
            newAlarms.add(newAlarm);
            alarmProcessor.setAlarms(accountId, senseId, newAlarms);

        } catch (Exception exception) {
            LOGGER.error("error=no-alarm-set error_msg={} account_id={}", exception.getMessage(), accountId);
            return GenericResult.failWithResponse(exception.getMessage(), SET_ALARM_ERROR_RESPONSE);
        }

        return GenericResult.ok(String.format(SET_ALARM_OK_RESPONSE, newAlarmString));
    }

    /**
     * only allow non-repeating, next-occurring alarm to be canceled
     */
    private GenericResult cancelAlarm(final Long accountId, final String senseId) {

        final Optional<UserInfo> alarmInfoOptional = this.mergedUserInfoDynamoDB.getInfo(senseId, accountId);
        if (!alarmInfoOptional.isPresent()) {
            LOGGER.warn("warning=no-user-info sense_id={} account_id={}", senseId, accountId);
            return GenericResult.failWithResponse(NO_USER_INFO, CANCEL_ALARM_ERROR_RESPONSE);
        }

        final UserInfo userInfo = alarmInfoOptional.get();

        if (userInfo.alarmList.isEmpty()) {
            LOGGER.warn("action=no-alarm-to-cancel reason=empty-alarm-list sense_id={} account_id={}", senseId, accountId);
            return GenericResult.failWithResponse(ERROR_NO_ALARM_TO_CANCEL, NO_ALARM_RESPONSE);
        }

        if (!userInfo.timeZone.isPresent()) {
            LOGGER.warn("action=no-alarm-cancel reason=missing-timezone sense_id={} account_id={}", senseId, accountId);
            return GenericResult.fail(NO_TIMEZONE);
        }

        final DateTimeZone timeZone = userInfo.timeZone.get();
        final DateTime now = DateTime.now(DateTimeZone.UTC);
        final Map<Long, Integer> ringTimeIndexMap = Maps.newTreeMap();
        final List<Alarm> newAlarms = Lists.newArrayList();

        int index = 0;
        for (final Alarm alarm : userInfo.alarmList) {
            if (!alarm.isEnabled) {
                newAlarms.add(alarm);
                continue;
            }

            final List<Alarm> alarms = Collections.singletonList(alarm);
            final RingTime nextRingTime = Alarm.Utils.generateNextRingTimeFromAlarmTemplatesForUser(alarms, now.getMillis(), timeZone);
            if (nextRingTime.expectedRingTimeUTC > 0) {
                ringTimeIndexMap.put(nextRingTime.expectedRingTimeUTC, index++);
            } else {
                newAlarms.add(alarm);
            }
        }

        // traverse map ordered by ring-time in chronological order
        boolean foundAlarm = false;
        Long canceledRingTime = 0L;
        boolean repeatingAlarm = false;
        for (final Long ringtime : ringTimeIndexMap.keySet()) {
            final int alarmIndex = ringTimeIndexMap.get(ringtime);
            final Alarm alarm = userInfo.alarmList.get(alarmIndex);

            if (!foundAlarm && ringtime > now.getMillis()) {
                if (!alarm.isRepeated) {
                    canceledRingTime = ringtime;
                    foundAlarm = true;
                    continue;
                } else {
                    repeatingAlarm = true;
                }
            }
            newAlarms.add(alarm);
        }


        if (newAlarms.size() == userInfo.alarmList.size()) {
            LOGGER.warn("action=no-alarm-to-cancel reason=no-eligible-alarms sense_id={} account_id={}", senseId, accountId);
            final String response = (repeatingAlarm) ? REPEATED_ALARM_CANCEL_INSTRUCTIONS : NO_ALARM_RESPONSE;
            return GenericResult.failWithResponse(ERROR_NO_ALARM_TO_CANCEL, response);
        }

        try {
            alarmProcessor.setAlarms(accountId, senseId, newAlarms);
        } catch (Exception exception) {
            LOGGER.error("error=set-alarm-fail error_msg={}", exception.getMessage());
            return GenericResult.failWithResponse(exception.getMessage(), CANCEL_ALARM_ERROR_RESPONSE);
        }

        final DateTime localNow = now.withZone(timeZone);
        final DateTime canceledDateTime = new DateTime(canceledRingTime, timeZone);
        String tomorrow = "";
        if (localNow.plusDays(1).getDayOfYear() == canceledDateTime.getDayOfYear()) {
            tomorrow = " tomorrow";
        }
        final String response = String.format(CANCEL_ALARM_OK_RESPONSE_TEMPLATE,
                canceledDateTime.toString("h:mm a") + tomorrow);
        return GenericResult.ok(response);
    }

    @Override
    public Integer matchAnnotations(final AnnotatedTranscript annotatedTranscript) {
        return annotatedTranscript.times.size();
    }

    @Override
    public SupichiResponseType responseType() {
        return SupichiResponseType.WATSON;
    }
}