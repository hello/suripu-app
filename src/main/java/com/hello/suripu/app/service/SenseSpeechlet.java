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
import com.hello.suripu.core.db.AlarmDAODynamoDB;
import com.hello.suripu.core.db.CalibrationDAO;
import com.hello.suripu.core.db.DeviceDataDAODynamoDB;
import com.hello.suripu.core.db.DeviceReadDAO;
import com.hello.suripu.core.db.MergedUserInfoDynamoDB;
import com.hello.suripu.core.db.sleep_sounds.DurationDAO;
import com.hello.suripu.core.preferences.AccountPreferencesDAO;
import com.hello.suripu.core.processors.SleepSoundsProcessor;
import com.hello.suripu.core.processors.TimelineProcessor;
import com.hello.suripu.coredw8.clients.MessejiClient;
import com.hello.suripu.coredw8.db.TimelineDAODynamoDB;
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
      final DeviceDataDAODynamoDB deviceDataDAO,
      final TimelineDAODynamoDB timelineDAODynamoDB,
      final MessejiClient messejiClient,
      final SleepSoundsProcessor sleepSoundsProcessor,
      final DurationDAO durationDAO,
      final TimelineProcessor timelineProcessor,
      final AccountPreferencesDAO preferencesDAO,
      final CalibrationDAO calibrationDAO,
      final MergedUserInfoDynamoDB mergedUserInfoDynamoDB,
      final AlarmDAODynamoDB alarmDAODynamoDB) {
    this.accountDAO = accountDAO;
    this.accessTokenDAO = accessTokenDAO;
    intentHandlers.add(new TemperatureIntentHandler(deviceReadDAO, deviceDataDAO, preferencesDAO));
    intentHandlers.add(new NameIntentHandler(accountDAO));
    intentHandlers.add(new ScoreIntentHandler(accountDAO, timelineDAODynamoDB, timelineProcessor));
    intentHandlers.add(new SleepSoundIntentHandler(deviceReadDAO, sleepSoundsProcessor, durationDAO, messejiClient));
    intentHandlers.add(new LastSleepSoundIntentHandler(deviceReadDAO, sleepSoundsProcessor, durationDAO, messejiClient));
    intentHandlers.add(new ConditionIntentHandler(deviceReadDAO, deviceDataDAO, preferencesDAO, calibrationDAO));
    intentHandlers.add(new AlarmIntentHandler(deviceReadDAO, sleepSoundsProcessor, durationDAO, mergedUserInfoDynamoDB, alarmDAODynamoDB));
  }

//  @Override
  public SpeechletResponse onIntent(IntentRequest request, Session session) throws SpeechletException {

    LOGGER.info("onIntent requestId={}, sessionId={}", request.getRequestId(), session.getSessionId());

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
        return ih.handleIntent(intent, session, accessToken);
      }
    }

    throw new SpeechletException("The Intent " + intentName + " is not recognized.");
  }

//  @Override
  public SpeechletResponse onLaunch(LaunchRequest request, Session session) throws SpeechletException {
    LOGGER.info("onLaunch requestId={}, sessionId={}", request.getRequestId(), session.getSessionId());
    return IntentHandler.buildSpeechletResponse("Hello, how can I help you?", false);
  }

//  @Override
  public void onSessionEnded(SessionEndedRequest request, Session session) throws SpeechletException {
    LOGGER.info("onSessionEnded requestId={}, sessionId={}", request.getRequestId(), session.getSessionId());

  }

//  @Override
  public void onSessionStarted(SessionStartedRequest request, Session session) throws SpeechletException {
    LOGGER.info("onSessionStarted requestId={}, sessionId={}", request.getRequestId(), session.getSessionId());

  }

}
