package com.hello.suripu.app.service;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.slu.Slot;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazon.speech.ui.PlainTextOutputSpeech;
import com.amazon.speech.ui.Reprompt;
import com.amazon.speech.ui.SimpleCard;
import com.amazonaws.AmazonServiceException;
import com.hello.suripu.core.db.AlarmDAODynamoDB;
import com.hello.suripu.core.db.DeviceReadDAO;
import com.hello.suripu.core.db.MergedUserInfoDynamoDB;
import com.hello.suripu.core.db.sleep_sounds.DurationDAO;
import com.hello.suripu.core.exceptions.TooManyAlarmsException;
import com.hello.suripu.core.models.Alarm;
import com.hello.suripu.core.models.AlarmSound;
import com.hello.suripu.core.models.AlarmSource;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.models.UserInfo;
import com.hello.suripu.core.processors.SleepSoundsProcessor;
import com.hello.suripu.core.translations.English;
import com.hello.suripu.core.util.AlarmUtils;
import com.hello.suripu.core.util.JsonError;
import com.hello.suripu.coredropwizard.oauth.AccessToken;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;


/**
 * Created by jnorgan on 6/16/16.
 */
public class AlarmIntentHandler extends IntentHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(AlarmIntentHandler.class);
  private static final String INTENT_NAME = "Alarm";

  final DeviceReadDAO deviceReadDAO;
  final MergedUserInfoDynamoDB mergedUserInfoDynamoDB;
  final SleepSoundsProcessor sleepSoundsProcessor;
  final DurationDAO durationDAO;
  final AlarmDAODynamoDB alarmDAODynamoDB;

  public AlarmIntentHandler(final DeviceReadDAO deviceReadDAO,
                            final SleepSoundsProcessor sleepSoundsProcessor,
                            final DurationDAO durationDAO,
                            final MergedUserInfoDynamoDB mergedUserInfoDynamoDB,
                            final AlarmDAODynamoDB alarmDAODynamoDB) {
    this.deviceReadDAO = deviceReadDAO;
    this.mergedUserInfoDynamoDB = mergedUserInfoDynamoDB;
    this.sleepSoundsProcessor = sleepSoundsProcessor;
    this.durationDAO = durationDAO;
    this.alarmDAODynamoDB = alarmDAODynamoDB;
  }

  @Override
  public SpeechletResponse handleIntentInternal(final Intent intent, final Session session, final AccessToken accessToken) {

    Boolean isTomorrow = false;
    final Slot timeSlot = intent.getSlot("Time");
    String slotTime;
    if (timeSlot == null || timeSlot.getValue() == null) {
      //default
      final String output = "Okay. What time would you like your alarm set for?";
      SimpleCard card = new SimpleCard();
      card.setTitle("Hello Sense");
      card.setContent(String.format("%s", output));

      PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
      speech.setText(output);

      PlainTextOutputSpeech repromptSpeech = new PlainTextOutputSpeech();
      repromptSpeech.setText("Sorry, I didn't get that. What time would you like your alarm set for?");

      final Reprompt reprompt = new Reprompt();
      reprompt.setOutputSpeech(repromptSpeech);

      return SpeechletResponse.newAskResponse(speech, reprompt, card);
    } else {
      slotTime = timeSlot.getValue();
    }

    final String[] timePieces = slotTime.split(":");



    final Long clientTime = DateTime.now().getMillis();

    if(!AlarmUtils.isWithinReasonableBounds(DateTime.now(), clientTime, 50000)) {
      LOGGER.error("account_id {} set alarm failed, client time too off.( was {}, now is {}", accessToken.accountId, clientTime, DateTime.now());
      throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).entity(
          new JsonError(Response.Status.BAD_REQUEST.getStatusCode(), English.ERROR_CLOCK_OUT_OF_SYNC)).build()
      );
    }

    final List<DeviceAccountPair> deviceAccountMap = deviceReadDAO.getSensesForAccountId(accessToken.accountId);
    if(deviceAccountMap.isEmpty()){
      LOGGER.error("Account {} tries to set alarm without connected to a Sense.", accessToken.accountId);
      throw new WebApplicationException(Response.Status.FORBIDDEN);
    }

    // Only update alarms in the account that linked with the most recent sense.
    final DeviceAccountPair deviceAccountPair = deviceAccountMap.get(0);
    final List<Alarm> alarms = Lists.newArrayList();

    try {

      alarms.addAll(getAlarms(deviceReadDAO, accessToken));

      final Optional<UserInfo> alarmInfoOptional = mergedUserInfoDynamoDB.getInfo(deviceAccountPair.externalDeviceId, accessToken.accountId);
      if (!alarmInfoOptional.isPresent()) {
        LOGGER.warn("No merge info for user {}, device {}", accessToken.accountId, deviceAccountPair.externalDeviceId);
        throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).build());
      }

      if (!alarmInfoOptional.get().timeZone.isPresent()) {
        LOGGER.warn("No user timezone set for account {}, device {}, alarm set skipped.", deviceAccountPair.accountId, deviceAccountPair.externalDeviceId);
        throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).build());
      }

      final DateTimeZone timeZone = alarmInfoOptional.get().timeZone.get();

      DateTime now = DateTime.now().toDateTime(timeZone);

      DateTime alarmTime = DateTime.now().toDateTime(timeZone)
          .withHourOfDay(Integer.valueOf(timePieces[0]))
          .withMinuteOfHour(Integer.valueOf(timePieces[1]))
          .withSecondOfMinute(0);

      LOGGER.debug("Now: {}, Alarm: {}", now.toString(), alarmTime.toString());

      if (alarmTime.isBeforeNow()) {
        isTomorrow = true;
        alarmTime = alarmTime.plusDays(1);
      }

      final Set<Integer> daysOfWeek = Sets.newHashSet();
      daysOfWeek.add(alarmTime.getDayOfWeek());

      final Alarm newAlarm = new Alarm.Builder()
          .withAlarmSound(new AlarmSound(5L, "Dusk"))
          .withYear(alarmTime.getYear())
          .withDay(alarmTime.getDayOfMonth())
          .withMonth(alarmTime.getMonthOfYear())
          .withDayOfWeek(daysOfWeek)
          .withHour(alarmTime.getHourOfDay())
          .withMinute(alarmTime.getMinuteOfHour())
          .withIsEnabled(true)
          .withIsSmart(false)
          .withIsRepeated(false)
          .withId(UUID.randomUUID().toString())
          .withSource(AlarmSource.VOICE_SERVICE)
          .build();

      alarms.add(newAlarm);


      final Alarm.Utils.AlarmStatus status = Alarm.Utils.isValidAlarms(alarms, DateTime.now(), timeZone);

      if (status.equals(Alarm.Utils.AlarmStatus.OK)) {
        if (!mergedUserInfoDynamoDB.setAlarms(deviceAccountPair.externalDeviceId, accessToken.accountId,
            alarmInfoOptional.get().lastUpdatedAt,
            alarmInfoOptional.get().alarmList,
            alarms,
            alarmInfoOptional.get().timeZone.get())) {
          LOGGER.warn("Cannot update alarm, race condition for account: {}", accessToken.accountId);
          return errorResponse("I was unable to set your alarm. Please try again or use the Sense app.");
        }
        alarmDAODynamoDB.setAlarms(accessToken.accountId, alarms);
      }

      if (status.equals(Alarm.Utils.AlarmStatus.SMART_ALARM_ALREADY_SET)) {
        LOGGER.error("Invalid alarm for account {}, device {}, alarm set skipped. Smart alarm already set.", deviceAccountPair.accountId, deviceAccountPair.externalDeviceId);
        return errorResponse("I can only set one smart alarm per day. You already have one set for this day.");
      }

      final String whichDay = (isTomorrow) ? "tomorrow" : "today";
      DateTimeFormatter fmt = DateTimeFormat.forPattern("hh:mm a");

      return buildSpeechletResponse(String.format("Your alarm has been set for %s at %s", whichDay, fmt.print(alarmTime)), true);

    } catch (AmazonServiceException awsException){
      LOGGER.error("Aws failed when user {} tries to get alarms.", accessToken.accountId);
      return errorResponse("I had a problem setting your alarm. Please try again.");
    } catch (TooManyAlarmsException tooManyAlarmException){
      LOGGER.error("Account {} tries to set {} alarm, too many alarm", accessToken.accountId, alarms.size());
      return errorResponse("I can't set more than thirty alarms.");
    } catch (Exception ex) {
      return errorResponse(ex.getMessage());
    }
  }

  @Override
  public String getIntentName() {
    return INTENT_NAME;
  }

  private List<Alarm> getAlarms(final DeviceReadDAO deviceDAO, final AccessToken token) throws Exception {
    LOGGER.debug("Before getting device account map from account_id");
    final List<DeviceAccountPair> deviceAccountMap = deviceDAO.getSensesForAccountId(token.accountId);
    if(deviceAccountMap.size() == 0){
      LOGGER.error("User {} tries to retrieve alarm without paired with a Sense.", token.accountId);
      throw new Exception("There's no Sense currently paired to your account.");
    }

    try {
      LOGGER.debug("Before getting device account map from account_id");
      final Optional<UserInfo> alarmInfoOptional = this.mergedUserInfoDynamoDB.getInfo(deviceAccountMap.get(0).externalDeviceId, token.accountId);
      LOGGER.debug("Fetched alarm info optional");

      if(!alarmInfoOptional.isPresent()){
        LOGGER.warn("Merge alarm info table doesn't have record for device {}, account {}.", deviceAccountMap.get(0).externalDeviceId, token.accountId);
        //                throw new WebApplicationException(Response.Status.BAD_REQUEST);
        return Collections.emptyList();
      }


      final UserInfo userInfo = alarmInfoOptional.get();
      if(!userInfo.timeZone.isPresent()){
        LOGGER.error("User {} tries to get alarm without having a time zone.", token.accountId);
        throw new Exception("Please update your timezone and try again.");
      }

      final DateTimeZone userTimeZone = userInfo.timeZone.get();
      final List<Alarm> smartAlarms = Alarm.Utils.disableExpiredNoneRepeatedAlarms(userInfo.alarmList, DateTime.now().getMillis(), userTimeZone);
      final Alarm.Utils.AlarmStatus status = Alarm.Utils.isValidAlarms(smartAlarms, DateTime.now(), userTimeZone);
      if(!status.equals(Alarm.Utils.AlarmStatus.OK)){
        LOGGER.error("Invalid alarm for user {} device {}", token.accountId, userInfo.deviceId);
        throw new Exception("I had some trouble getting your alarms. Please try again.");
      }

      return smartAlarms;
    }catch (AmazonServiceException awsException){
      LOGGER.error("Aws failed when user {} tries to get alarms.", token.accountId);
      throw new Exception("I had some trouble getting your alarms. Please try again.");
    }
  }
}
