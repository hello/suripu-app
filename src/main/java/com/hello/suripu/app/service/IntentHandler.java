package com.hello.suripu.app.service;

import com.google.common.collect.Lists;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazon.speech.ui.LinkAccountCard;
import com.amazon.speech.ui.PlainTextOutputSpeech;
import com.amazon.speech.ui.SimpleCard;
import com.hello.suripu.coredw8.oauth.AccessToken;

import java.util.Date;
import java.util.List;
import java.util.Random;

/**
 * Created by jnorgan on 6/16/16.
 */
public abstract class IntentHandler {

  final private List<String> okValues = Lists.newArrayList("Ok", "Allright", "Done!", "Will do", "as you wish");

  public IntentHandler() {

  }

  public boolean isResponsible(final String intentName) {
    return getIntentName().equals(intentName);
  }

  public SpeechletResponse handleIntent(final Intent intent, final Session session, final AccessToken accessToken) {

    return handleIntentInternal(intent, session, accessToken);
  }

  public abstract SpeechletResponse handleIntentInternal(final Intent intent, final Session session, final AccessToken accessToken);

  public abstract String getIntentName();

  protected SpeechletResponse errorResponse(final String error) {
    return buildSpeechletResponse(error, true);
  }


  protected SpeechletResponse randomOkResponse() {
    final Random r = new Random(new Date().getTime());
    final String okValue = okValues.get(r.nextInt(okValues.size()));

    return buildSpeechletResponse(okValue, true);
  }

  static public SpeechletResponse buildSpeechletResponse(final String output, final boolean shouldEndSession) {
    // Create the Simple card content.
    SimpleCard card = new SimpleCard();
    card.setTitle(SenseSpeechlet.SKILL_NAME);
    card.setContent(String.format("%s", output));

    // Create the plain text output.
    PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
    speech.setText(output);

    // Create the speechlet response.
    SpeechletResponse response = new SpeechletResponse();
    response.setShouldEndSession(shouldEndSession);
    response.setOutputSpeech(speech);
    response.setCard(card);
    return response;
  }

  static public SpeechletResponse buildLinkAccountResponse() {
    // Create the Simple card content.
    LinkAccountCard card = new LinkAccountCard();
    card.setTitle(SenseSpeechlet.SKILL_NAME);

    // Create the plain text output.
    PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
    speech.setText("Please go to your Alexa app and link your account.");

    // Create the speechlet response.
    return SpeechletResponse.newTellResponse(speech, card);
  }
}
