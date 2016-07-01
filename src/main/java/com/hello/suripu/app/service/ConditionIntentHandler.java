package com.hello.suripu.app.service;

import com.google.common.base.Optional;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.slu.Slot;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.hello.suripu.core.db.CalibrationDAO;
import com.hello.suripu.core.db.DeviceDataDAODynamoDB;
import com.hello.suripu.core.db.DeviceReadDAO;
import com.hello.suripu.core.models.Calibration;
import com.hello.suripu.core.models.CurrentRoomState;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.models.DeviceData;
import com.hello.suripu.core.preferences.AccountPreferencesDAO;
import com.hello.suripu.core.preferences.PreferenceName;
import com.hello.suripu.core.util.RoomConditionUtil;
import com.hello.suripu.coredw8.oauth.AccessToken;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Created by jnorgan on 6/16/16.
 */
public class ConditionIntentHandler extends IntentHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConditionIntentHandler.class);
  private static final String INTENT_NAME = "GetCondition";
  private static final Float NO_SOUND_FILL_VALUE_DB = 35.0f;

  final DeviceReadDAO deviceReadDAO;
  final DeviceDataDAODynamoDB deviceDataDAO;
  final AccountPreferencesDAO preferencesDAO;
  final CalibrationDAO calibrationDAO;

  public ConditionIntentHandler(final DeviceReadDAO deviceReadDAO,
                                final DeviceDataDAODynamoDB deviceDataDAO,
                                final AccountPreferencesDAO preferencesDAO,
                                final CalibrationDAO calibrationDAO) {
    this.deviceReadDAO = deviceReadDAO;
    this.deviceDataDAO = deviceDataDAO;
    this.preferencesDAO = preferencesDAO;
    this.calibrationDAO = calibrationDAO;
  }
  @Override
  public SpeechletResponse handleIntentInternal(final Intent intent, final Session session, final AccessToken accessToken) {

    final Slot slot = intent.getSlot("Condition");
    String condition;
    if (slot == null || slot.getValue() == null) {
      //default
      condition = "";
    } else {
      condition = slot.getValue();
    }

    LOGGER.debug("action=alexa-intent-condition account_id={} condition={}", accessToken.accountId.toString(), condition);

    final Optional<DeviceAccountPair> optionalPair = deviceReadDAO.getMostRecentSensePairByAccountId(accessToken.accountId);
    if(!optionalPair.isPresent()) {
      return errorResponse("No Account Found!");
    }
    final DeviceAccountPair accountPair = optionalPair.get();
    final DateTime maxDT = DateTime.now(DateTimeZone.UTC).plusMinutes(2);
    final DateTime minDT = DateTime.now(DateTimeZone.UTC).minusMinutes(30);
    final Optional<DeviceData> optionalData = deviceDataDAO.getMostRecent(accountPair.accountId, accountPair.externalDeviceId, maxDT, minDT);
    if (!optionalData.isPresent()) {
      return errorResponse("No Data Found!");
    }
    final DeviceData data = optionalData.get();

    final Map<PreferenceName, Boolean> preferences = preferencesDAO.get(accountPair.accountId);

    Integer thresholdInMinutes = 15;
    final Optional<Calibration> calibrationOptional = calibrationDAO.getStrict(accountPair.externalDeviceId);
    final Boolean hasDust = calibrationOptional.isPresent();
    final CurrentRoomState roomState = CurrentRoomState.fromDeviceData(data, DateTime.now(), thresholdInMinutes, "c", calibrationOptional, NO_SOUND_FILL_VALUE_DB).withDust(hasDust);

    String conditionUnits;
    Float conditionValue;
    String additionalComment;
    //Temperature
    switch(condition.toLowerCase()) {
      case "temperature":
        additionalComment = roomState.temperature.message.replace("*", "");
        final Float tempInCelsius = roomState.temperature.value;
        conditionUnits = "degrees celsius";
        conditionValue = tempInCelsius;

        if (preferences.containsKey(PreferenceName.TEMP_CELSIUS) && !preferences.get(PreferenceName.TEMP_CELSIUS)) {
          conditionUnits = "degrees fahrenheit";
          conditionValue = celsiusToFahrenheit(tempInCelsius);
        }
        break;

      case "humidity":
        additionalComment = roomState.humidity.message.replace("*", "");
        conditionUnits = "percent";
        conditionValue = roomState.humidity.value;
        break;

      case "light level":
        additionalComment = "";
        conditionUnits = "lux";
        conditionValue = roomState.light.value;
        additionalComment = roomState.light.message.replace("*", "");
        break;

      case "noise level":
        additionalComment = roomState.sound.message.replace("*", "");
        conditionUnits = "decibels";
        conditionValue = roomState.sound.value;
        break;
      default:
        final CurrentRoomState.State.Condition generalCondition = RoomConditionUtil.getGeneralRoomConditionV2(roomState, hasDust);
        if (generalCondition.equals(CurrentRoomState.State.Condition.IDEAL)) {
          return buildSpeechletResponse("Your bedroom is all set for a good night's sleep.", true);

        } else {

          final StringBuilder builder  = new StringBuilder();
          if (roomState.light.condition.equals(CurrentRoomState.State.Condition.ALERT)) {
            builder.append(roomState.light.message);
          }
          if (roomState.sound.condition.equals(CurrentRoomState.State.Condition.ALERT)) {
            builder.append(roomState.sound.message);
          }
          if (roomState.humidity.condition.equals(CurrentRoomState.State.Condition.ALERT)) {
            builder.append(roomState.humidity.message);
          }
          if (roomState.temperature.condition.equals(CurrentRoomState.State.Condition.ALERT)) {
            builder.append(roomState.temperature.message);
          }
          if (roomState.particulates.condition.equals(CurrentRoomState.State.Condition.ALERT)) {
            builder.append(roomState.particulates.message);
          }
          return buildSpeechletResponse(builder.toString(), true);
        }
    }
    return buildSpeechletResponse(String.format("It's currently %.0f %s. %s", conditionValue, conditionUnits, additionalComment), true);
  }

  @Override
  public String getIntentName() {
    return INTENT_NAME;
  }

  private static float celsiusToFahrenheit(final float value) {
    return ((value * 9.0f) / 5.0f) + 32.0f;
  }
}
