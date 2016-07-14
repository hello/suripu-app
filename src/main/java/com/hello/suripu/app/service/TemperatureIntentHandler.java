package com.hello.suripu.app.service;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.hello.suripu.core.db.DeviceDataDAODynamoDB;
import com.hello.suripu.core.db.DeviceReadDAO;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.models.DeviceData;
import com.hello.suripu.core.preferences.AccountPreferencesDAO;
import com.hello.suripu.core.preferences.PreferenceName;
import com.hello.suripu.coredw8.oauth.AccessToken;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Created by jnorgan on 6/16/16.
 */
public class TemperatureIntentHandler extends IntentHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(TemperatureIntentHandler.class);
  private static final ImmutableList<String> INTENTS_HANDLED = ImmutableList.of("GetTemperature");

  final DeviceReadDAO deviceReadDAO;
  final DeviceDataDAODynamoDB deviceDataDAO;
  final AccountPreferencesDAO preferencesDAO;
  final TestVoiceResponsesDAO voiceResponsesDAO;
  final Map<Long, Integer> accountIDInvocationCounts = Maps.newHashMap();

  public TemperatureIntentHandler(
      final DeviceReadDAO deviceReadDAO,
      final DeviceDataDAODynamoDB deviceDataDAO,
      final AccountPreferencesDAO preferencesDAO,
      final TestVoiceResponsesDAO voiceResponsesDAO) {
    this.deviceReadDAO = deviceReadDAO;
    this.deviceDataDAO = deviceDataDAO;
    this.preferencesDAO = preferencesDAO;
    this.voiceResponsesDAO = voiceResponsesDAO;
  }

  @Override
  public SpeechletResponse handleIntentInternal(final Intent intent, final Session session, final AccessToken accessToken) {
    LOGGER.debug("action=alexa-intent-temperature account_id={}", accessToken.accountId.toString());
    if(!accountIDInvocationCounts.containsKey(accessToken.accountId)) {
      accountIDInvocationCounts.put(accessToken.accountId, 0);
    } else {
      accountIDInvocationCounts.put(accessToken.accountId, accountIDInvocationCounts.get(accessToken.accountId) + 1);
    }

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
    final Float tempInCelsius = (float)(data.ambientTemperature - 389) / 100.0f;

    final ImmutableList<String> responses = voiceResponsesDAO.getAllResponsesByIntent(intent.getName());

    final String response = responses.get(accountIDInvocationCounts.get(accessToken.accountId) % responses.size());

    if (preferences.containsKey(PreferenceName.TEMP_CELSIUS) && preferences.get(PreferenceName.TEMP_CELSIUS)) {
      return buildSpeechletResponse(String.format(response.replace("{unit}", "celsius"), tempInCelsius), true);
    } else {
      return buildSpeechletResponse(String.format(response.replace("{unit}", "fahrenheit"), celsiusToFahrenheit(tempInCelsius)), true);
    }

    //Get room conditions from api

  }

  @Override
  public ImmutableList<String> getIntentsHandled() {
    return INTENTS_HANDLED;
  }

  private static float celsiusToFahrenheit(final float value) {
    return ((value * 9.0f) / 5.0f) + 32.0f;
  }
}
