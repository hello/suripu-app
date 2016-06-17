package com.hello.suripu.app.service;

import com.google.common.base.Optional;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.hello.suripu.core.db.DeviceDataDAODynamoDB;
import com.hello.suripu.core.db.DeviceReadDAO;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.models.DeviceData;
import com.hello.suripu.coredw8.oauth.AccessToken;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

/**
 * Created by jnorgan on 6/16/16.
 */
public class TemperatureIntentHandler extends IntentHandler {

  private static final String INTENT_NAME = "GetTemperature";

  final DeviceReadDAO deviceReadDAO;
  final DeviceDataDAODynamoDB deviceDataDAO;

  public TemperatureIntentHandler(final DeviceReadDAO deviceReadDAO, final DeviceDataDAODynamoDB deviceDataDAO) {
    this.deviceReadDAO = deviceReadDAO;
    this.deviceDataDAO = deviceDataDAO;
  }
  @Override
  public SpeechletResponse handleIntentInternal(final Intent intent, final AccessToken accessToken) {

//    return IntentHandler.buildLinkAccountResponse();
    final Optional<DeviceAccountPair> optionalPair = deviceReadDAO.getMostRecentSensePairByAccountId(accessToken.accountId);
    if(!optionalPair.isPresent()) {
      return errorResponse("No Account Found!");
    }
    final DeviceAccountPair accountPair = optionalPair.get();
    final DateTime maxDT = DateTime.now(DateTimeZone.UTC).plusMinutes(2);
    final DateTime minDT = DateTime.now(DateTimeZone.UTC).minusMinutes(30);
    final Optional<DeviceData> optionalData = deviceDataDAO.getMostRecent(1050L, accountPair.externalDeviceId, maxDT, minDT);
    if (!optionalData.isPresent()) {
      return errorResponse("No Data Found!");
    }
    final DeviceData data = optionalData.get();


    //Get room conditions from api
    return buildSpeechletResponse(String.format("The temperature is %.2f degrees celsius.", (float)(data.ambientTemperature - 389)/100.0f), true);
  }

  @Override
  public String getIntentName() {
    return INTENT_NAME;
  }
}
