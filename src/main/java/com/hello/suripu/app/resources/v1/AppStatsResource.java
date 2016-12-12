package com.hello.suripu.app.resources.v1;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import com.codahale.metrics.annotation.Timed;
import com.hello.suripu.app.utils.TokenCheckerFactory;
import com.hello.suripu.core.db.AccountDAO;
import com.hello.suripu.core.db.AppStatsDAO;
import com.hello.suripu.core.db.InsightsDAODynamoDB;
import com.hello.suripu.core.db.TimeZoneHistoryDAODynamoDB;
import com.hello.suripu.core.models.Account;
import com.hello.suripu.core.models.AppStats;
import com.hello.suripu.core.models.AppUnreadStats;
import com.hello.suripu.core.models.Insights.InsightCard;
import com.hello.suripu.core.models.Question;
import com.hello.suripu.core.models.TimeZoneHistory;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.core.processors.QuestionProcessor;
import com.hello.suripu.coredropwizard.oauth.AccessToken;
import com.hello.suripu.coredropwizard.oauth.Auth;
import com.hello.suripu.coredropwizard.oauth.ScopesAllowed;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.dropwizard.jersey.PATCH;

@Path("/v1/app/stats")
public class AppStatsResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppStatsResource.class);

    private final AppStatsDAO appStatsDAO;
    private final InsightsDAODynamoDB insightsDAO;
    private final QuestionProcessor questionProcessor;
    private final AccountDAO accountDAO;
    private final TimeZoneHistoryDAODynamoDB tzHistoryDAO;
    private final TokenCheckerFactory tokenCheckerFactory;


    public AppStatsResource(final AppStatsDAO appStatsDAO,
                            final InsightsDAODynamoDB insightsDAO,
                            final QuestionProcessor questionProcessor,
                            final AccountDAO accountDAO,
                            final TimeZoneHistoryDAODynamoDB tzHistoryDAO,
                            final TokenCheckerFactory tokenCheckerFactory) {
        this.appStatsDAO = appStatsDAO;
        this.insightsDAO = insightsDAO;
        this.questionProcessor = questionProcessor;
        this.accountDAO = accountDAO;
        this.tzHistoryDAO = tzHistoryDAO;
        this.tokenCheckerFactory = tokenCheckerFactory;
    }

    @ScopesAllowed({OAuthScope.APP_STATS})
    @Timed
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public AppStats getLastViewed(@Auth final AccessToken accessToken) {
        final Optional<DateTime> insightsLastViewed = appStatsDAO.getInsightsLastViewed(accessToken.accountId);
        final Optional<DateTime> questionsLastViewed = appStatsDAO.getQuestionsLastViewed(accessToken.accountId);
        return new AppStats(insightsLastViewed, questionsLastViewed);
    }

    @ScopesAllowed({OAuthScope.APP_STATS})
    @Timed
    @PATCH
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateLastViewed(@Auth final AccessToken accessToken,
                                     @Valid final AppStats appStats) {
        if (appStats.insightsLastViewed.isPresent()) {
            final DateTime insightsLastViewed = appStats.insightsLastViewed.get();
            appStatsDAO.putInsightsLastViewed(accessToken.accountId, insightsLastViewed);
        }

        if (appStats.questionsLastViewed.isPresent()) {
            final DateTime questionsLastViewed = appStats.questionsLastViewed.get();
            appStatsDAO.putQuestionsLastViewed(accessToken.accountId, questionsLastViewed);
        }

        if (appStats.questionsLastViewed.isPresent() || appStats.insightsLastViewed.isPresent()) {
            return Response.status(Response.Status.ACCEPTED).build();
        }

        return Response.status(Response.Status.NOT_MODIFIED).build();
    }

    @ScopesAllowed({OAuthScope.APP_STATS})
    @Timed
    @GET
    @Path("/unread")
    @Produces(MediaType.APPLICATION_JSON)
    public AppUnreadStats unread(@Auth final AccessToken accessToken) {
        final Long accountId = accessToken.accountId;

        final Optional<DateTime> insightsLastViewed = appStatsDAO.getInsightsLastViewed(accountId);

        final Optional<Boolean> hasUnreadInsights = insightsLastViewed.transform(new Function<DateTime, Boolean>() {
            @Override
            public Boolean apply(DateTime insightsLastViewed) {
                final Boolean chronological = false; // most recent first
                final DateTime queryDate = DateTime.now(DateTimeZone.UTC).plusDays(1); // matches InsightsResource
                final ImmutableList<InsightCard> insights = insightsDAO.getInsightsByDate(accountId, queryDate, chronological, 1);
                return (!insights.isEmpty() && insights.get(0).timestamp.isAfter(insightsLastViewed));
            }
        });

        final Optional<Integer> accountAgeInDays = getAccountAgeInDays(accountId);
        final Optional<DateTime> questionsLastViewed = appStatsDAO.getQuestionsLastViewed(accountId);
        final Optional<Boolean> hasUnansweredQuestions = accountAgeInDays.transform(new Function<Integer, Boolean>() {
            @Override
            public Boolean apply(Integer accountAgeInDays) {
                final int timeZoneOffset = getTimeZoneOffsetMillis(accountId);
                final DateTime today = DateTime.now(DateTimeZone.UTC).plusMillis(timeZoneOffset).withTimeAtStartOfDay();
                final List<Question> questions = questionProcessor.getQuestions(accountId, accountAgeInDays,
                        today, QuestionProcessor.DEFAULT_NUM_QUESTIONS, true);
                if (questionsLastViewed.isPresent()) {
                    final DateTime lastViewed = questionsLastViewed.get();
                    for (final Question question : questions) {
                        if (question.accountCreationDate.isAfter(lastViewed)) {
                            return true;
                        }
                    }

                    return false;
                }

                return !questions.isEmpty();
            }
        });

        final Thread tokenCheckerThread = new Thread(tokenCheckerFactory.create(accessToken), "Token Checker Thread");
        tokenCheckerThread.start();

        return new AppUnreadStats(hasUnreadInsights.or(false), hasUnansweredQuestions.or(false));
    }


    private int getTimeZoneOffsetMillis(final Long accountId) {
        final Optional<TimeZoneHistory> tzHistory = this.tzHistoryDAO.getCurrentTimeZone(accountId);
        if (tzHistory.isPresent()) {
            return tzHistory.get().offsetMillis;
        }

        return TimeZoneHistory.FALLBACK_OFFSET_MILLIS;
    }

    private Optional<Integer> getAccountAgeInDays(final Long accountId) {
        final Optional<Account> accountOptional = this.accountDAO.getById(accountId);
        return accountOptional.transform(new Function<Account, Integer>() {
            @Override
            public Integer apply(Account account) {
                return account.getAgeInDays();
            }
        });
    }
}
