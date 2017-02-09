package com.hello.suripu.app.resources.v1;

import com.google.common.base.Function;
import com.google.common.base.Optional;

import com.codahale.metrics.annotation.Timed;
import com.hello.suripu.core.actions.Action;
import com.hello.suripu.core.actions.ActionProcessor;
import com.hello.suripu.core.actions.ActionType;
import com.hello.suripu.core.db.AccountDAO;
import com.hello.suripu.core.db.TimeZoneHistoryDAODynamoDB;
import com.hello.suripu.core.models.Account;
import com.hello.suripu.core.models.Choice;
import com.hello.suripu.core.models.Question;
import com.hello.suripu.core.models.TimeZoneHistory;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.core.processors.QuestionProcessor;
import com.hello.suripu.core.processors.QuestionSurveyProcessor;
import com.hello.suripu.coredropwizard.oauth.AccessToken;
import com.hello.suripu.coredropwizard.oauth.Auth;
import com.hello.suripu.coredropwizard.oauth.ScopesAllowed;

import com.hello.suripu.coredropwizard.resources.BaseResource;
import com.librato.rollout.RolloutClient;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import java.util.Collections;
import java.util.List;

@Path("/v1/questions")
public class QuestionsResource extends BaseResource {

    @Inject
    RolloutClient feature;

    @Inject
    ActionProcessor actionProcessor;

    private static final Logger LOGGER = LoggerFactory.getLogger(QuestionsResource.class);

    private final AccountDAO accountDAO;
    private final TimeZoneHistoryDAODynamoDB tzHistoryDAO;
    private final QuestionProcessor questionProcessor;
    private final QuestionSurveyProcessor questionSurveyProcessor;

    public QuestionsResource(final AccountDAO accountDAO,
                             final TimeZoneHistoryDAODynamoDB tzHistoryDAO,
                             final QuestionProcessor questionProcessor,
                             final QuestionSurveyProcessor questionSurveyProcessor) {
        this.accountDAO = accountDAO;
        this.tzHistoryDAO = tzHistoryDAO;
        this.questionProcessor = questionProcessor;
        this.questionSurveyProcessor = questionSurveyProcessor;
    }

    @ScopesAllowed({OAuthScope.QUESTIONS_READ})
    @Timed
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Question> getQuestions(
            @Auth final AccessToken accessToken,
            @QueryParam("date") final String date) {

        LOGGER.debug("action=get_questions account_id={}", accessToken.accountId);
        final Optional<Integer> accountAgeInDays = this.getAccountAgeInDays(accessToken.accountId);
        if (!accountAgeInDays.isPresent()) {
            LOGGER.warn("warning=fail-to-get-account-age account_id={}", accessToken.accountId);
            throw new WebApplicationException(404);
        }

        final int timeZoneOffset = this.getTimeZoneOffsetMillis(accessToken.accountId);

        final DateTime now = DateTime.now(DateTimeZone.UTC);
        final DateTime todayLocal = now.plusMillis(timeZoneOffset).withTimeAtStartOfDay();
        final String todayLocalString = todayLocal.toString("yyyy-MM-dd");
        LOGGER.debug("today_local={}", todayLocalString);
        
        if(date != null && !date.equals(todayLocalString)) {
            LOGGER.debug("key=question-query-date-mismatch today_local={} date_param={} account_id={}", todayLocalString, date, accessToken.accountId);
            return Collections.emptyList();
        }

        // get question
        final List<Question> questionProcessorQuestions = this.questionProcessor.getQuestions(accessToken.accountId, accountAgeInDays.get(), todayLocal, QuestionProcessor.DEFAULT_NUM_QUESTIONS, true);
        if (!hasQuestionSurveyProcessorEnabled( accessToken.accountId )) {
            return questionProcessorQuestions;
        }

        final List<Question> questions = this.questionSurveyProcessor.getQuestions(accessToken.accountId, accountAgeInDays.get(), todayLocal, questionProcessorQuestions, timeZoneOffset);

        actionProcessor.add(new Action(accessToken.accountId, ActionType.QUESTION_GET, Optional.of(String.valueOf(questions.size())), now, Optional.of(timeZoneOffset)));

        return questions;
    }

    @ScopesAllowed({OAuthScope.QUESTIONS_READ})
    @Timed
    @GET
    @Path("/more")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Question> getMoreQuestions(
            @Auth final AccessToken accessToken) {

        // user asked for more questions
        LOGGER.debug("action=get_more_questions account_id={}", accessToken.accountId);
        final Optional<Integer> accountAgeInDays = this.getAccountAgeInDays(accessToken.accountId);
        if (!accountAgeInDays.isPresent()) {
            LOGGER.warn("warning=fail-to-get-account-age account_id={}", accessToken.accountId);
            throw new WebApplicationException(404);
        }

        final int timeZoneOffset = this.getTimeZoneOffsetMillis(accessToken.accountId);

        final DateTime today = DateTime.now(DateTimeZone.UTC).plusMillis(timeZoneOffset).withTimeAtStartOfDay();
        LOGGER.debug("action=found-more-questions account_id={} today={}", accessToken.accountId, today);

        // get question
        return this.questionProcessor.getQuestions(accessToken.accountId, accountAgeInDays.get(), today, QuestionProcessor.DEFAULT_NUM_MORE_QUESTIONS, false);
    }

    @ScopesAllowed({OAuthScope.QUESTIONS_WRITE})
    @Timed
    @POST
    @Path("/save")
    @Consumes(MediaType.APPLICATION_JSON)
    public void saveAnswers(@Auth final AccessToken accessToken,
                           @QueryParam("account_question_id") final Long accountQuestionId,
                           @Valid final List<Choice> choice) {
        LOGGER.debug("action=save-response account_id={} account_question_id={}", accessToken.accountId, accountQuestionId);

        final Optional<Integer> questionIdOptional = choice.get(0).questionId;
        Integer questionId = 0;
        if (questionIdOptional.isPresent()) {
            questionId = questionIdOptional.get();
        }

        this.questionProcessor.saveResponse(accessToken.accountId, questionId, accountQuestionId, choice);
        this.actionProcessor.add(new Action(accessToken.accountId, ActionType.QUESTION_SAVE, Optional.of(String.valueOf(questionId)), DateTime.now(DateTimeZone.UTC), Optional.absent()));
    }

    @ScopesAllowed({OAuthScope.QUESTIONS_WRITE})
    @Timed
    @PUT
    @Path("/skip")
    @Consumes(MediaType.APPLICATION_JSON)
    public void skipQuestion(@Auth final AccessToken accessToken,
                             @QueryParam("id") final Integer questionId,
                             @QueryParam("account_question_id") final Long accountQuestionId) {
        LOGGER.debug("action=skip-question question_id={} account_id={}", questionId, accessToken.accountId);

        final int timeZoneOffset = this.getTimeZoneOffsetMillis(accessToken.accountId);
        this.questionProcessor.skipQuestion(accessToken.accountId, questionId, accountQuestionId, timeZoneOffset);
        this.actionProcessor.add(new Action(accessToken.accountId, ActionType.QUESTION_SKIP, Optional.of(String.valueOf(questionId)), DateTime.now(DateTimeZone.UTC), Optional.of(timeZoneOffset)));
    }

    // keeping these for backward compatibility
    @ScopesAllowed({OAuthScope.QUESTIONS_WRITE})
    @Timed
    @POST
    @Deprecated
    @Consumes(MediaType.APPLICATION_JSON)
    public void saveAnswer(@Auth final AccessToken accessToken, @Valid final Choice choice) {
        LOGGER.debug("action=saving-answer account_id={} choice={}", accessToken.accountId, choice.id);
    }

    @ScopesAllowed({OAuthScope.QUESTIONS_WRITE})
    @Timed
    @PUT
    @Deprecated
    @Path("/{question_id}/skip")
    @Consumes(MediaType.APPLICATION_JSON)
    public void skipQuestion(@Auth final AccessToken accessToken,
                             @PathParam("question_id") final Long questionId) {
        LOGGER.debug("action=skipping-question question={} account_id={}", questionId, accessToken.accountId);
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
