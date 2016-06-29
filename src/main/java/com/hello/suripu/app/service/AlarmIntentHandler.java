package com.hello.suripu.app.service;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.slu.Slot;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazonaws.AmazonServiceException;
import com.hello.suripu.core.db.AlarmDAODynamoDB;
import com.hello.suripu.core.db.DeviceReadDAO;
import com.hello.suripu.core.db.MergedUserInfoDynamoDB;
import com.hello.suripu.core.db.sleep_sounds.DurationDAO;
import com.hello.suripu.core.exceptions.TooManyAlarmsException;
import com.hello.suripu.core.models.Alarm;
import com.hello.suripu.core.models.AlarmSound;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.models.UserInfo;
import com.hello.suripu.core.processors.SleepSoundsProcessor;
import com.hello.suripu.core.translations.English;
import com.hello.suripu.core.util.AlarmUtils;
import com.hello.suripu.core.util.JsonError;
import com.hello.suripu.coredw8.oauth.AccessToken;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    LOGGER.debug("action=alexa-intent-alarm account_id={}", accessToken.accountId.toString());

    final Slot timeSlot = intent.getSlot("Time");
    String slotTime;
    if (timeSlot == null || timeSlot.getValue() == null) {
      //default
      slotTime = "17:00";
    } else {
      slotTime = timeSlot.getValue();
    }

    final String[] timePieces = slotTime.split(":");
    final List<Alarm> alarms = Lists.newArrayList();
    final Set<Integer> daysOfWeek = Sets.newHashSet();
    daysOfWeek.add(2);
    final Alarm newAlarm = new Alarm.Builder()
        .withAlarmSound(new AlarmSound(5L, "Dusk"))
        .withYear(2016)
        .withDay(28)
        .withMonth(6)
        .withDayOfWeek(daysOfWeek)
        .withHour(Integer.getInteger(timePieces[0]))
        .withMinute(Integer.getInteger(timePieces[1]))
        .withIsEnabled(true)
        .withIsSmart(false)
        .withIsRepeated(false)
        .withId(UUID.randomUUID().toString())
        .build();

    alarms.add(newAlarm);

    final Long clientTime = DateTime.now().getMillis();

    final DateTime now = DateTime.now();
    if(!AlarmUtils.isWithinReasonableBounds(now, clientTime, 50000)) {
      LOGGER.error("account_id {} set alarm failed, client time too off.( was {}, now is {}", accessToken.accountId, clientTime, now);
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
    try {
      final Optional<UserInfo> alarmInfoOptional = mergedUserInfoDynamoDB.getInfo(deviceAccountPair.externalDeviceId, accessToken.accountId);
      if(!alarmInfoOptional.isPresent()){
        LOGGER.warn("No merge info for user {}, device {}", accessToken.accountId, deviceAccountPair.externalDeviceId);
        throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).build());
      }

      if(!alarmInfoOptional.get().timeZone.isPresent()){
        LOGGER.warn("No user timezone set for account {}, device {}, alarm set skipped.", deviceAccountPair.accountId, deviceAccountPair.externalDeviceId);
        throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).build());
      }

      final DateTimeZone timeZone = alarmInfoOptional.get().timeZone.get();
      final Alarm.Utils.AlarmStatus status = Alarm.Utils.isValidAlarms(alarms, DateTime.now(), timeZone);

      if(status.equals(Alarm.Utils.AlarmStatus.OK)) {
        if(!mergedUserInfoDynamoDB.setAlarms(deviceAccountPair.externalDeviceId, accessToken.accountId,
            alarmInfoOptional.get().lastUpdatedAt,
            alarmInfoOptional.get().alarmList,
            alarms,
            alarmInfoOptional.get().timeZone.get())){
          LOGGER.warn("Cannot update alarm, race condition for account: {}", accessToken.accountId);
          return errorResponse("I was unable to set your alarm. Please try again or use the Sense app.");
        }
        alarmDAODynamoDB.setAlarms(accessToken.accountId, alarms);
      }

      if(status.equals(Alarm.Utils.AlarmStatus.SMART_ALARM_ALREADY_SET)){
        LOGGER.error("Invalid alarm for account {}, device {}, alarm set skipped. Smart alarm already set.", deviceAccountPair.accountId, deviceAccountPair.externalDeviceId);
        return errorResponse("I can only set one smart alarm per day. You already have one set for this day.");
      }


    }catch (AmazonServiceException awsException){
      LOGGER.error("Aws failed when user {} tries to get alarms.", accessToken.accountId);
      return errorResponse("I had a problem setting your alarm. Please try again.");
    }catch (TooManyAlarmsException tooManyAlarmException){
      LOGGER.error("Account {} tries to set {} alarm, too many alarm", accessToken.accountId, alarms.size());
      return errorResponse("I can't set more than thirty alarms.");
    }


    return buildSpeechletResponse("Your alarm is now set for...", false);

  }

  @Override
  public String getIntentName() {
    return INTENT_NAME;
  }
}
