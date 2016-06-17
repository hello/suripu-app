package com.hello.suripu.app.service;

import com.google.common.base.Optional;
import com.google.common.collect.Sets;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.speechlet.IntentRequest;
import com.amazon.speech.speechlet.LaunchRequest;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SessionEndedRequest;
import com.amazon.speech.speechlet.SessionStartedRequest;
import com.amazon.speech.speechlet.Speechlet;
import com.amazon.speech.speechlet.SpeechletException;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.hello.suripu.core.db.AccountDAO;
import com.hello.suripu.core.db.DeviceDataDAODynamoDB;
import com.hello.suripu.core.db.DeviceReadDAO;
import com.hello.suripu.coredw8.oauth.AccessToken;
import com.hello.suripu.core.oauth.AccessTokenUtils;
import com.hello.suripu.coredw8.db.AccessTokenDAO;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.UUID;

/**
 * Created by jnorgan on 6/16/16.
 */
public class SenseSpeechlet implements Speechlet {

  private static final Logger LOGGER = LoggerFactory.getLogger(SenseSpeechlet.class);
  public static String SKILL_NAME = "Hello Sense";

  private Set<IntentHandler> intentHandlers = Sets.newHashSet();
  private final AccountDAO accountDAO;
  private final AccessTokenDAO accessTokenDAO;

  public SenseSpeechlet(
      final AccountDAO accountDAO,
      final AccessTokenDAO accessTokenDAO,
      final DeviceReadDAO deviceReadDAO,
      final DeviceDataDAODynamoDB deviceDataDAO) {
    this.accountDAO = accountDAO;
    this.accessTokenDAO = accessTokenDAO;
    intentHandlers.add(new TemperatureIntentHandler(deviceReadDAO, deviceDataDAO));
    intentHandlers.add(new NameIntentHandler(accountDAO));
  }

//  @Override
  public SpeechletResponse onIntent(IntentRequest request, Session session) throws SpeechletException {

    //Do token check
    if (session.getUser().getAccessToken() == null) {
      return IntentHandler.buildLinkAccountResponse();
    }
    final String dirtyToken = session.getUser().getAccessToken();

    final Optional<UUID> optionalUUID = AccessTokenUtils.cleanUUID(dirtyToken);
    if (!optionalUUID.isPresent()) {
      LOGGER.error("error=access-token-invalid token={}", dirtyToken);
      return IntentHandler.buildLinkAccountResponse();
    }
    final UUID uuid = optionalUUID.get();

    final Optional<AccessToken> optionalAccessToken = accessTokenDAO.getByAccessToken(uuid);
    if (!optionalAccessToken.isPresent()) {
      LOGGER.error("error=access-token-unknown token={}", uuid.toString());
      return IntentHandler.buildLinkAccountResponse();
    }
    final AccessToken accessToken = optionalAccessToken.get();

    if (accessToken.hasExpired(DateTime.now(DateTimeZone.UTC))){
      LOGGER.error("error=access-token-expired", uuid.toString());
      return IntentHandler.buildLinkAccountResponse();
    }

    final Intent intent = request.getIntent();
    final String intentName = intent.getName();

    for (IntentHandler ih : intentHandlers) {
      if (ih.isResponsible(intentName)) {
        return ih.handleIntent(intent, accessToken);
      }
    }

    throw new SpeechletException("The Intent " + intentName + " is not recognized.");
  }

//  @Override
  public SpeechletResponse onLaunch(LaunchRequest request, Session session) throws SpeechletException {
    LOGGER.debug("onLaunch");
    return IntentHandler.buildSpeechletResponse("Hello, how can I help you?", false);
  }

//  @Override
  public void onSessionEnded(SessionEndedRequest request, Session session) throws SpeechletException {
    LOGGER.debug("onSessionEnded");

  }

//  @Override
  public void onSessionStarted(SessionStartedRequest request, Session session) throws SpeechletException {
    LOGGER.debug("onSessionStarted");

  }

}
