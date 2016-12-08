package com.hello.suripu.app.resources.v1;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.hello.suripu.app.configuration.ExpansionConfiguration;
import com.hello.suripu.core.db.AccountDAO;
import com.hello.suripu.core.db.AppStatsDAO;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.db.InsightsDAODynamoDB;
import com.hello.suripu.core.db.TimeZoneHistoryDAODynamoDB;
import com.hello.suripu.core.models.Account;
import com.hello.suripu.core.models.AppStats;
import com.hello.suripu.core.models.AppUnreadStats;
import com.hello.suripu.core.models.DeviceAccountPair;
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
import is.hello.gaibu.core.models.Configuration;
import is.hello.gaibu.core.models.Expansion;
import is.hello.gaibu.core.models.ExpansionData;
import is.hello.gaibu.core.models.ExpansionDeviceData;
import is.hello.gaibu.core.models.ExternalToken;
import is.hello.gaibu.core.stores.ExpansionStore;
import is.hello.gaibu.core.stores.ExternalOAuthTokenStore;
import is.hello.gaibu.core.stores.PersistentExpansionDataStore;
import is.hello.gaibu.homeauto.factories.HomeAutomationExpansionDataFactory;
import is.hello.gaibu.homeauto.factories.HomeAutomationExpansionFactory;
import is.hello.gaibu.homeauto.interfaces.HomeAutomationExpansion;

@Path("/v1/app/stats")
public class AppStatsResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppStatsResource.class);

    private final AppStatsDAO appStatsDAO;
    private final InsightsDAODynamoDB insightsDAO;
    private final QuestionProcessor questionProcessor;
    private final AccountDAO accountDAO;
    private final TimeZoneHistoryDAODynamoDB tzHistoryDAO;
    private final DeviceDAO deviceDAO;
    private final ExpansionConfiguration expansionConfig;
    private final ExpansionStore<Expansion> expansionStore;
    private final ExternalOAuthTokenStore<ExternalToken> externalTokenStore;
    private final PersistentExpansionDataStore expansionDataStore;
    private final ObjectMapper mapper;

    public AppStatsResource(final AppStatsDAO appStatsDAO,
                            final InsightsDAODynamoDB insightsDAO,
                            final QuestionProcessor questionProcessor,
                            final AccountDAO accountDAO,
                            final TimeZoneHistoryDAODynamoDB tzHistoryDAO,
                            final DeviceDAO deviceDAO,
                            final ExpansionConfiguration expansionConfig,
                            final ExpansionStore<Expansion> expansionStore,
                            final ExternalOAuthTokenStore<ExternalToken> externalTokenStore,
                            final PersistentExpansionDataStore expansionDataStore,
                            final ObjectMapper mapper) {
        this.appStatsDAO = appStatsDAO;
        this.insightsDAO = insightsDAO;
        this.questionProcessor = questionProcessor;
        this.accountDAO = accountDAO;
        this.tzHistoryDAO = tzHistoryDAO;
        this.deviceDAO = deviceDAO;
        this.expansionConfig = expansionConfig;
        this.expansionStore = expansionStore;
        this.externalTokenStore = externalTokenStore;
        this.expansionDataStore = expansionDataStore;
        mapper.registerModule(new JodaModule());
        this.mapper = mapper;
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

        final Thread tokenCheckerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    //Check that the deviceId had an enabled expansion

                    final Optional<Expansion> expansionOptional = expansionStore.getApplicationByName(Expansion.ServiceName.NEST.toString());
                    if(!expansionOptional.isPresent()) {
                        LOGGER.error("error=expansion-not-found app_name=Nest");
                        return;
                    }

                    final Expansion expansion = expansionOptional.get();

                    final List<DeviceAccountPair> sensePairedWithAccount = deviceDAO.getSensesForAccountId(accessToken.accountId);

                    if(sensePairedWithAccount.size() == 0){
                        LOGGER.error("error=no-sense-paired account_id={}", accessToken.accountId);
                        return;
                    }

                    final String deviceId = sensePairedWithAccount.get(0).externalDeviceId;

                    final Integer tokenCount = externalTokenStore.getActiveTokenCount(deviceId, expansion.id);
                    if(tokenCount < 1) {
                        LOGGER.debug("debug=no-active-tokens-found service_name={} device_id={}", Expansion.ServiceName.NEST.toString(), deviceId);
                        return;
                    }

                    final Optional<ExpansionData> expDataOptional = expansionDataStore.getAppData(expansion.id, deviceId);
                    if(!expDataOptional.isPresent()) {
                        LOGGER.error("error=no-ext-app-data expansion_id={} device_id={}", expansion.id, deviceId);
                        return;
                    }

                    final ExpansionData expData = expDataOptional.get();

                    if(expData.data.isEmpty()){
                        LOGGER.error("error=no-ext-app-data expansion_id={} device_id={}", expansion.id, deviceId);
                        return;
                    }

                    final Optional<ExpansionDeviceData> expansionDeviceDataOptional = HomeAutomationExpansionDataFactory.getAppData(mapper, expData.data, expansion.serviceName);

                    if(!expansionDeviceDataOptional.isPresent()){
                        LOGGER.error("error=bad-expansion-data service_name={} device_id={}", Expansion.ServiceName.NEST.toString(), deviceId);
                        return;
                    }

                    final ExpansionDeviceData appData = expansionDeviceDataOptional.get();

                    final Optional<String> decryptedTokenOptional = externalTokenStore.getDecryptedExternalToken(deviceId, expansion, false);
                    if(!decryptedTokenOptional.isPresent()) {
                        LOGGER.warn("warning=token-decrypt-failed service_name={} device_id={}", Expansion.ServiceName.NEST.toString(), deviceId);
                        return;
                    }

                    final String decryptedToken = decryptedTokenOptional.get();

                    final Optional<HomeAutomationExpansion> homeAutomationExpansionOptional = HomeAutomationExpansionFactory.getEmptyExpansion(expansionConfig.hueAppName(), expansion.serviceName, appData, decryptedToken);
                    if(!expansionOptional.isPresent()){
                        LOGGER.error("error=expansion-retrieval-failure service_name={} device_id={}", Expansion.ServiceName.NEST.toString(), deviceId);
                        return;
                    }

                    final HomeAutomationExpansion homeAutomationExpansion = homeAutomationExpansionOptional.get();
                    //Check status of token with external service
                    final Optional<List<Configuration>> configsOptional = homeAutomationExpansion.getConfigurations();
                    if(!configsOptional.isPresent()) {
                        LOGGER.info("info=disabling-invalid-token service_name={} device_id={}", Expansion.ServiceName.NEST.toString(), deviceId);
                        //disable the unusable token to force refresh (manual re-auth for Nest)
                        externalTokenStore.disableByDeviceId(deviceId, expansion.id);
                    }

                    LOGGER.debug("debug=valid-expansion-token service_name={} device_id={}", Expansion.ServiceName.NEST.toString(), deviceId);
                } catch (Exception exception) {

                }
            }
        }, "TokenCheck Thread");

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
