package com.hello.suripu.app.service;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.hello.suripu.coredw8.oauth.AccessToken;

/**
 * Created by jnorgan on 6/16/16.
 */
public class TemperatureIntentHandler extends IntentHandler {

  private static final String INTENT_NAME = "GetTemperature";

  @Override
  public SpeechletResponse handleIntentInternal(final Intent intent, final AccessToken accessToken) {

    //Get room conditions from api
    final Float temp = 23.12f;
    return buildSpeechletResponse(String.format("The temperature is %.1f degrees. Well, not really. That is a static value.", temp), true);
  }

  @Override
  public String getIntentName() {
    return INTENT_NAME;
  }
}
