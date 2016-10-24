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
import is.hello.supichi.db.SpeechCommandDAO;
import is.hello.supichi.commandhandlers.results.GenericResult;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static is.hello.supichi.commandhandlers.ErrorText.NO_TIMEZONE;
import static is.hello.supichi.models.HandlerResult.EMPTY_COMMAND;

/**
 * Created by ksg on 6/17/16
 */
public class AlarmHandler extends BaseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlarmHandler.class);

    private static final int MIN_ALARM_MINUTES_FROM_NOW = 5;

    private static final String CANCEL_ALARM_REGEX = "(cancel|delete|remove|unset).*(?:alarm)(s?)";
    private static final Pattern CANCEL_ALARM_PATTERN = Pattern.compile(CANCEL_ALARM_REGEX);

    private static final String SET_ALARM_REGEX = "((set).*(?:alarm))|(wake me)";
    private static final Pattern SET_ALARM__PATTERN = Pattern.compile(SET_ALARM_REGEX);

    public static final AlarmSound DEFAULT_ALARM_SOUND = new AlarmSound(5, "Dusk", "");

    // TODO: these responses should be moved to a dedicated AlarmResponseBuilder
    public static final String DUPLICATE_ALARM_RESPONSE = "Sorry, no alarm was set, you already have an alarm set for %s";
    public static final String SET_ALARM_ERROR_RESPONSE = "Sorry, we're unable to set your alarm. Please try again later";
    public static final String SET_ALARM_OK_RESPONSE = "Ok, your alarm is set for %s";
    public static final String SET_ALARM_ERROR_TOO_SOON_RESPONSE = "Sorry, we're unable to set your alarm. Please set a time greater than 5 minutes from now";
    public static final String SET_ALARM_ERROR_NO_TIME_RESPONSE = "Sorry, we're unable to set your alarm. Please specify an alarm time.";
    public static final String SET_ALARM_ERROR_NO_TIME_ZONE = "Sorry, we're unable to set your alarm. Please set your timezone in the app.";

    public static final String CANCEL_ALARM_ERROR_RESPONSE = "Sorry, we're unable to cancel your alarm. Please try again later.";
    public static final String CANCEL_ALARM_OK_RESPONSE = "OK, your alarm is canceled.";
    public static final String NO_ALARM_RESPONSE = "There is no alarm to cancel.";

    // error text
    public static final String NO_TIME_ERROR = "no time given";
    public static final String NO_USER_INFO = "no user info";
    public static final String TOO_SOON_ERROR = "alarm time too soon";
    public static final String DUPLICATE_ERROR = "duplicate alarm";

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
        return tempMap;
    }

    @Override
    Optional<SpeechCommand> getCommand(final String text) {
        final Matcher cancelMatcher = CANCEL_ALARM_PATTERN.matcher(text);
        if (cancelMatcher.find()) {
            return Optional.of (SpeechCommand.ALARM_DELETE);
        }

        final Matcher setMatcher = SET_ALARM__PATTERN.matcher(text);
        if (setMatcher.find()) {
            return Optional.of(SpeechCommand.ALARM_SET);
        }

        return Optional.absent();
    }

    @Override
    public HandlerResult executeCommand(final AnnotatedTranscript annotatedTranscript, final VoiceRequest request) {
        // TODO
        final Optional<SpeechCommand> optionalCommand = getCommand(annotatedTranscript.transcript);

        final Long accountId = request.accountId;
        final String senseId = request.senseId;

        if (!optionalCommand.isPresent()) {
            return new HandlerResult(HandlerType.ALARM, EMPTY_COMMAND, GenericResult.fail("no alarm set"));
        }

        final String command = optionalCommand.get().getValue();

        final GenericResult alarmResult;
        if (optionalCommand.get().equals(SpeechCommand.ALARM_SET)) {
            alarmResult = setAlarm(accountId, senseId, annotatedTranscript);
        } else {
            alarmResult = cancelAlarm(accountId, senseId, annotatedTranscript);
        }

        return new HandlerResult(HandlerType.ALARM, command, alarmResult);
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
            LOGGER.error("error=no-alarm-set reason=no-time-given text={} account={}", annotatedTranscript.transcript, accountId);
            return GenericResult.failWithResponse(NO_TIME_ERROR, SET_ALARM_ERROR_NO_TIME_RESPONSE);
        }


        final TimeAnnotation timeAnnotation = annotatedTranscript.times.get(0); // note time is in utc, need to convert
        final DateTimeZone timezoneId = DateTimeZone.forTimeZone(annotatedTranscript.timeZoneOptional.get());
        final DateTime annotatedTimeUTC = new DateTime(timeAnnotation.dateTime(), DateTimeZone.UTC);
        final DateTime now = DateTime.now(DateTimeZone.UTC);

        final DateTime alarmTimeLocal =  (annotatedTimeUTC.isAfter(now)) ? annotatedTimeUTC.withZone(timezoneId) : annotatedTimeUTC.plusDays(1).withZone(timezoneId);
        final DateTime localNow = now.withZone(timezoneId);

        LOGGER.debug("action=create-alarm-time account_id={} annotation_time_utc={} now_utc={} local_alarm_time={} local_now={}",
                accountId, annotatedTimeUTC.toString(), now, alarmTimeLocal.toString(), localNow.toString());

        // check alarm time is more than 5 minutes from localNow TODO: set minutes to 0 or something??
        if (alarmTimeLocal.withSecondOfMinute(0).withMillisOfSecond(0).isBefore(localNow.plusMinutes(MIN_ALARM_MINUTES_FROM_NOW))) {
            LOGGER.error("error=alarm-time-too-soon local_now={} alarm_now={}", localNow, alarmTimeLocal);
            return GenericResult.failWithResponse(TOO_SOON_ERROR, SET_ALARM_ERROR_TOO_SOON_RESPONSE);
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
                .withDayOfWeek(Sets.newHashSet(alarmTimeLocal.getDayOfWeek()))
                .withIsRepeated(false)
                .withAlarmSound(DEFAULT_ALARM_SOUND)
                .withIsEnabled(true)
                .withIsEditable(true)
                .withIsSmart(false)
                .withSource(AlarmSource.VOICE_SERVICE)
                .build();

        final List<Alarm> alarms = Lists.newArrayList();
        alarms.addAll(alarmProcessor.getAlarms(accountId, senseId));

        // check that alarm is not a duplicate
        for (final Alarm alarm : alarms) {
            if (alarm.equals(newAlarm)) {
                // duplicate alarm
                LOGGER.error("error=no-alarm-set reason=duplicate-alarm alarm={} account_id={}", newAlarm.toString());
                return GenericResult.failWithResponse(DUPLICATE_ERROR, String.format(DUPLICATE_ALARM_RESPONSE, newAlarmString));
            }
        }

        // TODO: remove voice alarms in the past to avoid filling up DDB item buffer

        // okay to set alarm
        try {
            alarms.add(newAlarm);
            alarmProcessor.setAlarms(accountId, senseId, alarms);

        } catch (Exception exception) {
            LOGGER.error("error=no-alarm-set error_msg={} account_id={}", exception.getMessage(), accountId);
            return GenericResult.failWithResponse(exception.getMessage(), SET_ALARM_ERROR_RESPONSE);
        }

        return GenericResult.ok(String.format(SET_ALARM_OK_RESPONSE, newAlarmString));
    }

    /**
     * only allow non-repeating, next-occurring alarm to be canceled
     */
    private GenericResult cancelAlarm(final Long accountId, final String senseId, final AnnotatedTranscript annotatedTranscript) {

        final Optional<UserInfo> alarmInfoOptional = this.mergedUserInfoDynamoDB.getInfo(senseId, accountId);
        if (!alarmInfoOptional.isPresent()) {
            return GenericResult.fail(NO_USER_INFO);
        }

        final UserInfo userInfo = alarmInfoOptional.get();
        if (!userInfo.timeZone.isPresent()) {
            return GenericResult.fail(NO_TIMEZONE);
        }

        final DateTimeZone timeZone = userInfo.timeZone.get();
        final DateTime now = DateTime.now(DateTimeZone.UTC);
        final Map<Long, Integer> ringTimeIndexMap = Maps.newTreeMap();

        int index = 0;
        for (final Alarm alarm : userInfo.alarmList) {
            final List<Alarm> alarms = Collections.singletonList(alarm);
            final RingTime nextRingTime = Alarm.Utils.generateNextRingTimeFromAlarmTemplatesForUser(alarms, now.getMillis(), timeZone);
            ringTimeIndexMap.put(nextRingTime.expectedRingTimeUTC, index++);
        }

        // traverse map ordered by ringtime in chronological order
        final List<Alarm> newAlarms = Lists.newArrayList();
        boolean foundAlarm = false;
        for (final Long ringtime : ringTimeIndexMap.keySet()) {
            final int alarmIndex = ringTimeIndexMap.get(ringtime);
            final Alarm alarm = userInfo.alarmList.get(alarmIndex);

            if (!foundAlarm && ringtime > now.getMillis()) {
                foundAlarm = true;
                if (!alarm.isRepeated && alarm.isEnabled) {
                    continue;
                }
            }
            newAlarms.add(alarm);
        }


        if (newAlarms.size() == userInfo.alarmList.size()) {
            return GenericResult.fail(NO_ALARM_RESPONSE);
        }

        try {
            alarmProcessor.setAlarms(accountId, senseId, newAlarms);
        } catch (Exception exception) {
            return GenericResult.failWithResponse(exception.getMessage(), CANCEL_ALARM_ERROR_RESPONSE);
        }

        return GenericResult.ok(CANCEL_ALARM_OK_RESPONSE);
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