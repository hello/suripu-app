package com.hello.suripu.app.service;

import com.google.common.base.Optional;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.hello.suripu.core.db.AccountDAO;
import com.hello.suripu.core.db.SleepStatsDAO;
import com.hello.suripu.core.models.Account;
import com.hello.suripu.core.models.AggregateSleepStats;
import com.hello.suripu.core.util.DateTimeUtil;
import com.hello.suripu.coredropwizard.db.TimelineDAODynamoDB;
import com.hello.suripu.coredropwizard.oauth.AccessToken;
import com.hello.suripu.coredropwizard.timeline.InstrumentedTimelineProcessor;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Days;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Created by jnorgan on 6/16/16.
 */
public class ScoreIntentHandler extends IntentHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(ScoreIntentHandler.class);
  private static final String INTENT_NAME = "GetScore";

  private final AccountDAO accountDAO;
  private final TimelineDAODynamoDB timelineDAODynamoDB;
  private final InstrumentedTimelineProcessor timelineProcessor;
  private final SleepStatsDAO sleepStatsDAO;

  public ScoreIntentHandler(final AccountDAO accountDAO,
                            final TimelineDAODynamoDB timelineDAODynamoDB,
                            final InstrumentedTimelineProcessor timelineProcessor,
                            final SleepStatsDAO sleepStatsDAO) {
    this.accountDAO = accountDAO;
    this.timelineDAODynamoDB = timelineDAODynamoDB;
    this.timelineProcessor = timelineProcessor;
    this.sleepStatsDAO = sleepStatsDAO;
  }

  @Override
  public SpeechletResponse handleIntentInternal(final Intent intent, final Session session, final AccessToken accessToken) {

    final DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM-dd");
    String slotDate = new DateTime().toString(formatter);

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

    final Optional<AggregateSleepStats> optionalStats = sleepStatsDAO.getSingleStat(account.id.get(), targetDate.toString(formatter));
    if (optionalStats.isPresent()) {
     final Integer sleepScore = optionalStats.get().sleepScore;
      if (sleepScore > 0) {
        return buildSpeechletResponse(String.format("Your sleep score was %d.", sleepScore), true);
      }
    }

    return buildSpeechletResponse("I was unable to get your sleep score. Please try again later.", true);
  }

  @Override
  public String getIntentName() {
    return INTENT_NAME;
  }
}
