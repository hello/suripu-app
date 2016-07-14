package com.hello.suripu.app.service;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazon.speech.ui.Image;
import com.amazon.speech.ui.LinkAccountCard;
import com.amazon.speech.ui.PlainTextOutputSpeech;
import com.amazon.speech.ui.Reprompt;
import com.amazon.speech.ui.StandardCard;
import com.hello.suripu.coredw8.oauth.AccessToken;

import java.util.Date;
import java.util.List;
import java.util.Random;

/**
 * Created by jnorgan on 6/16/16.
 */
public abstract class IntentHandler {

  public static final List<String> okValues = Lists.newArrayList("Ok", "Allright", "Done!", "Will do", "as you wish");

  public IntentHandler() {

  }

  public boolean isResponsible(final String intentName) {
    return getIntentsHandled().contains(intentName);
  }

  public SpeechletResponse handleIntent(final Intent intent, final Session session, final AccessToken accessToken) {

    return handleIntentInternal(intent, session, accessToken);
  }

  public abstract SpeechletResponse handleIntentInternal(final Intent intent, final Session session, final AccessToken accessToken);

  public abstract ImmutableList<String> getIntentsHandled();

  protected SpeechletResponse errorResponse(final String error) {
    return buildSpeechletResponse(error, true);
  }


  static public SpeechletResponse randomOkResponse() {
    final Random r = new Random(new Date().getTime());
    final String okValue = okValues.get(r.nextInt(okValues.size()));

    return buildSpeechletResponse(okValue, true);
  }

  static public SpeechletResponse buildSpeechletResponse(final String output, final boolean shouldEndSession) {
    // Create the Simple card content.
    StandardCard card = new StandardCard();

    card.setTitle(SenseSpeechlet.SKILL_NAME);
    card.setText(String.format("%s", output));
    Image cardImage = new Image();
    cardImage.setSmallImageUrl("https://s3.amazonaws.com/hello-dev/hello_logo_low_resolution_alexa.png");
    cardImage.setLargeImageUrl("https://s3.amazonaws.com/hello-dev/hello_logo_high_resolution_alexa.png");
    card.setImage(cardImage);

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

  static public SpeechletResponse buildSpeechletResponseWithReprompt(final String output, final String repromptText) {

    StandardCard card = new StandardCard();
    card.setTitle(SenseSpeechlet.SKILL_NAME);
    card.setText(String.format("%s", output));
    Image cardImage = new Image();
    cardImage.setSmallImageUrl("https://s3.amazonaws.com/hello-dev/hello_logo_low_resolution_alexa.png");
    cardImage.setLargeImageUrl("https://s3.amazonaws.com/hello-dev/hello_logo_high_resolution_alexa.png");
    card.setImage(cardImage);

    PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
    speech.setText(output);

    PlainTextOutputSpeech repromptSpeech = new PlainTextOutputSpeech();
    repromptSpeech.setText(repromptText);

    final Reprompt reprompt = new Reprompt();
    reprompt.setOutputSpeech(repromptSpeech);

    SpeechletResponse response = new SpeechletResponse();
    response.setReprompt(reprompt);
    response.setOutputSpeech(speech);
    response.setShouldEndSession(false);
    response.setCard(card);

    return response;
  }
}
