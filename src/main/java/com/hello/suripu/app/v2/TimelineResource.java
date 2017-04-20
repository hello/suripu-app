package com.hello.suripu.app.v2;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.hello.suripu.core.actions.Action;
import com.hello.suripu.core.actions.ActionProcessor;
import com.hello.suripu.core.actions.ActionResult;
import com.hello.suripu.core.actions.ActionType;
import com.hello.suripu.core.db.FeedbackDAO;
import com.hello.suripu.core.db.PillDataDAODynamoDB;
import com.hello.suripu.core.db.SleepStatsDAODynamoDB;
import com.hello.suripu.core.db.TimelineLogDAO;
import com.hello.suripu.core.logging.DataLogger;
import com.hello.suripu.core.models.AggregateSleepStats;
import com.hello.suripu.core.models.Event;
import com.hello.suripu.core.models.TimelineFeedback;
import com.hello.suripu.core.models.TimelineResult;
import com.hello.suripu.core.models.TrackerMotion;
import com.hello.suripu.core.models.timeline.v2.EventType;
import com.hello.suripu.core.models.timeline.v2.ScoreCondition;
import com.hello.suripu.core.models.timeline.v2.Timeline;
import com.hello.suripu.core.models.timeline.v2.TimelineEvent;
import com.hello.suripu.core.models.timeline.v2.TimelineLog;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.core.translations.English;
import com.hello.suripu.core.util.DateTimeUtil;
import com.hello.suripu.core.util.FeedbackUtils;
import com.hello.suripu.core.util.JsonError;
import com.hello.suripu.coredropwizard.db.TimelineDAODynamoDB;
import com.hello.suripu.coredropwizard.oauth.AccessToken;
import com.hello.suripu.coredropwizard.oauth.Auth;
import com.hello.suripu.coredropwizard.oauth.ScopesAllowed;
import com.hello.suripu.coredropwizard.resources.BaseResource;
import com.hello.suripu.coredropwizard.timeline.InstrumentedTimelineProcessor;
import com.hello.suripu.coredropwizard.timeline.InstrumentedTimelineProcessorV3;
import com.librato.rollout.RolloutClient;
import io.dropwizard.jersey.PATCH;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("/v2/timeline")
public class TimelineResource extends BaseResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineResource.class);

    @Inject
    RolloutClient feature;

    @Inject
    ActionProcessor actionProcessor;

    private final InstrumentedTimelineProcessor timelineProcessor;
    private final InstrumentedTimelineProcessorV3 timelineProcessorv3;

    private final TimelineDAODynamoDB timelineDAODynamoDB;
    private final TimelineLogDAO timelineLogDAO;
    private final FeedbackDAO feedbackDAO;
    private final PillDataDAODynamoDB pillDataDAODynamoDB;
    private final SleepStatsDAODynamoDB sleepStatsDAODynamoDB;
    private final DataLogger timelineLogDAOV2;

    public TimelineResource(final TimelineDAODynamoDB timelineDAODynamoDB,
                            final InstrumentedTimelineProcessor timelineProcessor,
                            final InstrumentedTimelineProcessorV3 timelineProcessorv3,
                            final TimelineLogDAO timelineLogDAO,
                            final FeedbackDAO feedbackDAO,
                            final PillDataDAODynamoDB pillDataDAODynamoDB,
                            final SleepStatsDAODynamoDB sleepStatsDAODynamoDB,
                            final DataLogger timelineLogDAOV2) {
        this.timelineProcessor = timelineProcessor;
        this.timelineProcessorv3 = timelineProcessorv3;
        this.timelineDAODynamoDB = timelineDAODynamoDB;
        this.timelineLogDAO = timelineLogDAO;
        this.feedbackDAO = feedbackDAO;
        this.pillDataDAODynamoDB = pillDataDAODynamoDB;
        this.sleepStatsDAODynamoDB = sleepStatsDAODynamoDB;
        this.timelineLogDAOV2 = timelineLogDAOV2;
    }

    @ScopesAllowed({OAuthScope.SLEEP_TIMELINE})
    @GET
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{datetime}")
    public Timeline getTimelineForNight(@Auth final AccessToken accessToken,
                                        @PathParam("datetime") final String datetime) {
        final String queryDate =  getQueryDate(datetime);
        final Optional<Integer> queryHourOptional = getOptionalQueryHour(datetime);
        return getTimelineForDateAndHourInternal(accessToken.accountId, queryDate, Optional.absent(), Optional.<TimelineFeedback>absent());
    }

 @ScopesAllowed({OAuthScope.SLEEP_FEEDBACK})
    @PATCH
    @Timed
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{datetime}/events/{type}/{timestamp}")
    public Timeline amendTimeOfEvent(@Auth final AccessToken accessToken,
                                     @PathParam("datetime") String datetime,
                                     @PathParam("type") String type,
                                     @PathParam("timestamp") long timestamp,
                                     @Valid TimelineEvent.TimeAmendment timeAmendment) {
        final String queryDate = getQueryDate(datetime);
        Optional<Integer> hourOptional = getOptionalQueryHour(datetime);
        final Integer offsetMillis = getOffsetMillis(accessToken.accountId, queryDate, timestamp);
        final DateTime oldEventDateTime = new DateTime(timestamp, DateTimeZone.UTC).plusMillis(offsetMillis);
        final String hourMinute = oldEventDateTime.toString(DateTimeFormat.forPattern("HH:mm"));
        final Event.Type eventType = Event.Type.fromInteger(EventType.fromString(type).value);

        final TimelineFeedback timelineFeedback = TimelineFeedback.createTimeAmendedFeedback(queryDate, hourMinute, timeAmendment.newEventTime, eventType, accessToken.accountId);

        //make sure the feedback event order and times make sense
        checkValidFeedbackOrThrow(accessToken.accountId,timelineFeedback, offsetMillis);

        timelineDAODynamoDB.invalidateCache(accessToken.accountId, timelineFeedback.dateOfNight, DateTime.now());

        //get the updated timeline
        final Timeline timeline = getTimelineForDateAndHourInternal(accessToken.accountId, queryDate, hourOptional, Optional.of(timelineFeedback));

        //make sure the feedback did not screw up the timeline
        checkValidTimelineOrThrow(accessToken.accountId,timeline);

        //if we made it this far, insert
        feedbackDAO.insertTimelineFeedback(accessToken.accountId, timelineFeedback);

        actionProcessor.add(new Action(accessToken.accountId, ActionType.TIMELINE_FEEDBACK, Optional.of(ActionResult.OKAY.string()), DateTime.now(DateTimeZone.UTC), Optional.absent()));
        return timeline;
    }

    @ScopesAllowed({OAuthScope.SLEEP_FEEDBACK})
    @DELETE
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{datetime}/events/{type}/{timestamp}")
    public Response deleteEvent(@Auth final AccessToken accessToken,
                                @PathParam("datetime") String datetime,
                                @PathParam("type") String type,
                                @PathParam("timestamp") long timestamp) {

        final String queryDate = getQueryDate(datetime);
        Optional<Integer> hourOptional = getOptionalQueryHour(datetime);

        final Integer offsetMillis = getOffsetMillis(accessToken.accountId, queryDate, timestamp);

        final DateTime incorrectEvent = new DateTime(timestamp, DateTimeZone.UTC).plusMillis(offsetMillis);
        final String hourMinute = incorrectEvent.toString(DateTimeFormat.forPattern("HH:mm"));
        final Event.Type eventType = Event.Type.fromInteger(EventType.fromString(type).value);

        final TimelineFeedback timelineFeedback = TimelineFeedback.createMarkedIncorrect(queryDate, hourMinute, eventType, accessToken.accountId);
        checkValidFeedbackOrThrow(accessToken.accountId,timelineFeedback,offsetMillis);

        feedbackDAO.insertTimelineFeedback(accessToken.accountId, timelineFeedback);

        //TODO in the future, if we delete an intermediate event (bathroom break at night), we will have to change the internal API to let retrieveTimelinesFast know that an event was removed
        return Response.status(Response.Status.ACCEPTED)
                //might have an hour
                       .entity(getTimelineForDateAndHourInternal(accessToken.accountId, queryDate,hourOptional, Optional.<TimelineFeedback>absent()))
                .build();
    }

    @ScopesAllowed({OAuthScope.SLEEP_FEEDBACK})
    @PUT
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{datetime}/events/{type}/{timestamp}")
    public Response validateEvent(@Auth final AccessToken accessToken,
                                  @PathParam("datetime") String datetime,
                                  @PathParam("type") String type,
                                  @PathParam("timestamp") long timestamp) {

        final String queryDate =  getQueryDate(datetime);
        final Optional<Integer> queryHourOptional = getOptionalQueryHour(datetime);

        final Integer offsetMillis = getOffsetMillis(accessToken.accountId, queryDate, timestamp);

        final DateTime correctEvent = new DateTime(timestamp, DateTimeZone.UTC).plusMillis(offsetMillis);
        final String hourMinute = correctEvent.toString(DateTimeFormat.forPattern("HH:mm"));
        final Event.Type eventType = Event.Type.fromInteger(EventType.fromString(type).value);

        // Correct event means feedback = prediction
        final TimelineFeedback timelineFeedback = TimelineFeedback.createMarkedCorrect(queryDate, hourMinute, eventType, accessToken.accountId);
        checkValidFeedbackOrThrow(accessToken.accountId,timelineFeedback,offsetMillis);

        //recalculate with feedback and check
        final Timeline timeline = getTimelineForDateAndHourInternal(accessToken.accountId, queryDate,queryHourOptional, Optional.of(timelineFeedback));

        //check to make sure sleep score did not get screwed up
        checkValidTimelineOrThrow(accessToken.accountId,timeline);

        //if we made it this far, insert
        feedbackDAO.insertTimelineFeedback(accessToken.accountId, timelineFeedback);

        actionProcessor.add(new Action(accessToken.accountId, ActionType.TIMELINE_CORRECT, Optional.of(ActionResult.OKAY.string()), DateTime.now(DateTimeZone.UTC), Optional.absent()));

        return Response.status(Response.Status.ACCEPTED).build();
    }

    private Integer getOffsetMillis(final Long accountId, final String date, final Long timestamp) {
        final Optional<AggregateSleepStats> aggregateSleepStatsOptional = sleepStatsDAODynamoDB.getSingleStat(accountId, date);

        if(aggregateSleepStatsOptional.isPresent()) {
            return aggregateSleepStatsOptional.get().offsetMillis;
        }

        LOGGER.warn("Missing aggregateSleepStats for account_id = {} and date = {}", accountId, date);
        LOGGER.warn("Querying trackerMotion table for offset for account_id = {} and date = {}", accountId, date);
        final DateTime startDateTime = DateTimeUtil.ymdStringToDateTime(date);
        final DateTime endDateTime = startDateTime.plusHours(48);

        final List<TrackerMotion> trackerMotionList = pillDataDAODynamoDB.getBetween(accountId, startDateTime, endDateTime);
        if(trackerMotionList.isEmpty()) {
            LOGGER.error("No tracker motion data");
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).entity(new JsonError(404, "Not found")).build());
        }

        Integer offsetMillis = trackerMotionList.get(0).offsetMillis;
        for(final TrackerMotion trackerMotion : trackerMotionList) {
            if(trackerMotion.timestamp >= timestamp) {
                offsetMillis = trackerMotion.offsetMillis;
                break;
            }
        }

        return offsetMillis;
    }

    private void checkValidFeedbackOrThrow(final long accountId, final TimelineFeedback timelineFeedback, final int offsetMillis) {

        //do not check validity of events that are not sleep events
        final boolean isSleepEvent = timelineFeedback.eventType.equals(Event.Type.IN_BED)
                || timelineFeedback.eventType.equals(Event.Type.SLEEP)
                || timelineFeedback.eventType.equals(Event.Type.WAKE_UP)
                || timelineFeedback.eventType.equals(Event.Type.OUT_OF_BED);

        if (!isSleepEvent) {
            return;
        }

        final FeedbackUtils feedbackUtils = new FeedbackUtils();
        final ImmutableList<TimelineFeedback> existingFeedbacks = feedbackDAO.getCorrectedForNight(accountId,timelineFeedback.dateOfNight);

        //proposed event is valid
        if (!feedbackUtils.checkEventValidity(timelineFeedback,offsetMillis)) {
            LOGGER.info("rejected feedback from account_id {} for evening of {} because new event was not valid",accountId,timelineFeedback.dateOfNight);
            throw new WebApplicationException(Response.status(Response.Status.PRECONDITION_FAILED).entity(new JsonError(Response.Status.PRECONDITION_FAILED.getStatusCode(), English.FEEDBACK_AT_INVALID_TIME)).build());
        }

        //events out of order
        if (!feedbackUtils.checkEventOrdering(existingFeedbacks,timelineFeedback,offsetMillis)) {
            LOGGER.info("rejected feedback from account_id {} for evening of {} because new event was out of order",accountId,timelineFeedback.dateOfNight);
            throw new WebApplicationException(Response.status(Response.Status.PRECONDITION_FAILED).entity(new JsonError(Response.Status.PRECONDITION_FAILED.getStatusCode(), English.FEEDBACK_INCONSISTENT)).build());
        }
    }

    private void checkValidTimelineOrThrow(final long accountId,final Timeline timeline) {

        if (!this.hasInvalidSleepScoreFromFeedbackChecking(accountId)) {
            return;
        }

        //IF there was new feedback being tried out, and if it resulted in the score condition becoming unavailable
        //THEN throw an error to the user
        if (timeline.scoreCondition.equals(ScoreCondition.UNAVAILABLE) ) {
            LOGGER.info("rejected feedback from account_id {} for evening of {} because it resulted in the sleep score becoming unavailable",accountId,timeline.dateNight);
            throw new WebApplicationException(Response.status(Response.Status.PRECONDITION_FAILED).entity(new JsonError(Response.Status.PRECONDITION_FAILED.getStatusCode(), English.FEEDBACK_CAUSED_INVALID_SLEEP_SCORE)).build());
        }


        if (timeline.events.isEmpty()) {
            LOGGER.info("rejected feedback from account_id {} for evening of {} because it resulted in an empty timeline",accountId,timeline.dateNight);
            throw new WebApplicationException(Response.status(Response.Status.PRECONDITION_FAILED).entity(new JsonError(Response.Status.PRECONDITION_FAILED.getStatusCode(), English.FEEDBACK_CAUSED_INVALID_SLEEP_SCORE)).build());
        }
    }

    private Timeline getTimelineForDateAndHourInternal(final long accountId, final String night, final Optional<Integer> hourOptional, final Optional<TimelineFeedback> newFeedback){
        final DateTime targetDate = DateTimeUtil.ymdStringToDateTime(night);

        final TimelineResult timelineResult;
        if (hourOptional.isPresent() && hasTimelineProcessorV3(accountId)){
            timelineResult = timelineProcessorv3.retrieveTimelinesFast(accountId, targetDate, hourOptional,newFeedback);
        } else {
            timelineResult = timelineProcessor.retrieveTimelinesFast(accountId, targetDate, hourOptional,newFeedback);
        }
        //GET THE TIMELINE WITH THE NEW FEEDBACK (or no new feedback)
        //TODO change timeline result to a V2 timeline result

        //GET THE V2 RESULT
        final Timeline timeline = Timeline.fromV1(timelineResult.timelines.get(0), timelineResult.dataCompleteness);

    //SEND LOG TO KINESIS
        if (timelineResult.logV2.isPresent()) {
            final TimelineLog logV2 = timelineResult.logV2.get();
            final String partitionKey = logV2.getPartitionKey();
            timelineLogDAOV2.putAsync(partitionKey, logV2.toProtoBuf());

            //TODO deprecate this sometime soon
            timelineLogDAO.putTimelineLog(accountId, logV2.getAsV1Log());
        }

        // log actions
        final ActionResult actionResult = (timelineResult.timelines.get(0).score.equals(0)) ? ActionResult.NO_DATA : ActionResult.OKAY;
        final String actionResultString = String.format("%s_%s", night, actionResult.string());
        final Optional<Integer> timeZoneOffset = (!timeline.events.isEmpty()) ? Optional.of(timeline.events.get(0).timezoneOffset): Optional.absent();
        actionProcessor.add(new Action(accountId, ActionType.TIMELINE_V2, Optional.of(actionResultString), DateTime.now(DateTimeZone.UTC), timeZoneOffset));

        return timeline;
    }

    private static final String getQueryDate(final String queryDateTimeString){
        if (queryDateTimeString.length() == 10){
            return queryDateTimeString;
        }
        final String queryDateString= queryDateTimeString.substring(0, 10);
        return queryDateString;
    }

    private static final Optional<Integer>  getOptionalQueryHour(final String queryDateTimeString){
        if (queryDateTimeString.length() == 10){
            return Optional.absent();
        }
        final DateTimeFormatter dateTimeFormatter= ISODateTimeFormat.dateOptionalTimeParser();
        final DateTime queryDateTime = dateTimeFormatter.parseDateTime(queryDateTimeString);
        return Optional.of(queryDateTime.getHourOfDay());
    }

}
