package com.hello.suripu.app.service;

import com.google.common.base.Optional;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.hello.suripu.core.db.AccountDAO;
import com.hello.suripu.core.models.Account;
import com.hello.suripu.core.models.TimelineFeedback;
import com.hello.suripu.core.models.TimelineResult;
import com.hello.suripu.core.util.DateTimeUtil;
import com.hello.suripu.coredw8.db.TimelineDAODynamoDB;
import com.hello.suripu.coredw8.oauth.AccessToken;
import com.hello.suripu.coredw8.timeline.InstrumentedTimelineProcessor;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Days;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;


/**
 * Created by jnorgan on 6/16/16.
 */
public class ScoreIntentHandler extends IntentHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(ScoreIntentHandler.class);
  private static final String INTENT_NAME = "GetScore";

  private final AccountDAO accountDAO;
  private final TimelineDAODynamoDB timelineDAODynamoDB;
  private final InstrumentedTimelineProcessor timelineProcessor;

  public ScoreIntentHandler(final AccountDAO accountDAO,
                            final TimelineDAODynamoDB timelineDAODynamoDB,
                            final InstrumentedTimelineProcessor timelineProcessor) {
    this.accountDAO = accountDAO;
    this.timelineDAODynamoDB = timelineDAODynamoDB;
    this.timelineProcessor = timelineProcessor;
  }

  @Override
  public SpeechletResponse handleIntentInternal(final Intent intent, final Session session, final AccessToken accessToken) {

    LOGGER.debug("action=alexa-intent-score account_id={}", accessToken.accountId.toString());

    String slotDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

    if (intent.getSlots().containsKey("Date") && intent.getSlot("Date") != null && intent.getSlot("Date").getValue() != null) {
      slotDate = intent.getSlot("Date").getValue();
      LOGGER.debug("action=alexa-intent-score-date account_id={} slot_date={}", accessToken.accountId.toString(), slotDate);
    }

    DateTime targetDate = DateTimeUtil.ymdStringToDateTime(slotDate).minusDays(1).withHourOfDay(0).withMinuteOfHour(0).withSecondOfMinute(0);

    if (intent.getSlots().containsKey("Duration") && intent.getSlot("Duration") != null && intent.getSlot("Duration").getValue() != null) {
      final String durationText = intent.getSlot("Duration").getValue();
      LOGGER.debug("action=alexa-intent-score-duration account_id={} duration_text={}", accessToken.accountId.toString(), durationText);
      final Days daysAgo = Days.parseDays(durationText);
      targetDate = new DateTime(DateTimeZone.UTC).minus(daysAgo).withHourOfDay(0).withMinuteOfHour(0).withSecondOfMinute(0);
    }

    LOGGER.debug("Target Date: {}", slotDate, targetDate.toString());

    //Get Username
    final Optional<Account> optionalAccount = accountDAO.getById(accessToken.accountId);
    if(!optionalAccount.isPresent()) {
      return errorResponse("I can't find your account. You must not exist. Think about that.");
    }
    final Account account = optionalAccount.get();

    final TimelineResult result = timelineProcessor.retrieveTimelinesFast(accessToken.accountId, targetDate, Optional.<TimelineFeedback>absent());
    if(result.timelines.isEmpty()) {
      return errorResponse(String.format("I'm unable to find any timelines for that account on %s", slotDate));
    }

    final Integer sleepScore = result.timelines.get(0).score;

    return buildSpeechletResponse(String.format("Your sleep score was %d.", sleepScore), true);
  }

  @Override
  public String getIntentName() {
    return INTENT_NAME;
  }
}
