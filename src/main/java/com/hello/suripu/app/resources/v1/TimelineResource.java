package com.hello.suripu.app.resources.v1;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.db.SleepLabelDAO;
import com.hello.suripu.core.db.SleepScoreDAO;
import com.hello.suripu.core.db.TrackerMotionDAO;
import com.hello.suripu.core.models.Event;
import com.hello.suripu.core.models.Insight;
import com.hello.suripu.core.models.SensorReading;
import com.hello.suripu.core.models.SleepSegment;
import com.hello.suripu.core.models.SleepStats;
import com.hello.suripu.core.models.Timeline;
import com.hello.suripu.core.models.TrackerMotion;
import com.hello.suripu.core.oauth.AccessToken;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.core.oauth.Scope;
import com.hello.suripu.core.processors.PartnerMotion;
import com.hello.suripu.core.util.DateTimeUtil;
import com.hello.suripu.core.util.SunData;
import com.hello.suripu.core.util.TimelineUtils;
import com.yammer.metrics.annotation.Timed;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;

@Path("/v1/timeline")
public class TimelineResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineResource.class);

    private final TrackerMotionDAO trackerMotionDAO;
    private final DeviceDAO deviceDAO;
    private final SleepScoreDAO sleepScoreDAO;
    private final SleepLabelDAO sleepLabelDAO;
    private final int dateBucketPeriod;
    private final SunData sunData;
    public TimelineResource(final TrackerMotionDAO trackerMotionDAO,
                            final DeviceDAO deviceDAO,
                            final SleepLabelDAO sleepLabelDAO,
                            final SleepScoreDAO sleepScoreDAO,
                            final int dateBucketPeriod,
                            final SunData sunData) {
        this.trackerMotionDAO = trackerMotionDAO;
        this.deviceDAO = deviceDAO;
        this.sleepLabelDAO = sleepLabelDAO;
        this.sleepScoreDAO = sleepScoreDAO;
        this.dateBucketPeriod = dateBucketPeriod;
        this.sunData = sunData;
    }

    @Timed
    @Path("/{date}")
    @Produces(MediaType.APPLICATION_JSON)
    @GET
    public List<Timeline> getTimelines(
            @Scope(OAuthScope.SLEEP_TIMELINE)final AccessToken accessToken,
            @PathParam("date") String date) {


        final DateTime targetDate = DateTime.parse(date, DateTimeFormat.forPattern(DateTimeUtil.DYNAMO_DB_DATE_FORMAT))
                .withZone(DateTimeZone.UTC).withHourOfDay(20);
        final DateTime endDate = targetDate.plusHours(14);
        LOGGER.debug("Target date: {}", targetDate);
        LOGGER.debug("End date: {}", endDate);

        final int threshold = 10; // events with scores < threshold will be considered motion events
        final int mergeThreshold = 1; // min segment size is 1 minute

        // TODO: compute this threshold dynamically

        final List<TrackerMotion> trackerMotions = trackerMotionDAO.getBetweenLocalUTC(accessToken.accountId, targetDate, endDate);
        LOGGER.debug("Length of trackerMotion: {}", trackerMotions.size());

        if(trackerMotions.isEmpty()) {
            LOGGER.debug("No data for account_id = {} and day = {}", accessToken.accountId, targetDate);
            final Timeline timeline = new Timeline(0, "You haven't been sleeping!", date, new ArrayList<SleepSegment>(), new ArrayList<Insight>());
            final List<Timeline> timelines = new ArrayList<>();
            timelines.add(timeline);
            return timelines;
        }

        // create sleep-motion segments
        final List<SleepSegment> segments = TimelineUtils.generateSleepSegments(trackerMotions, threshold, true);
        List<SleepSegment> categorized = TimelineUtils.categorizeSleepDepth(segments);

        final List<SleepSegment> extraSegments = new ArrayList<>();

        // detect sleep time
        final Optional<SleepSegment> sleepTimeSegment = TimelineUtils.computeSleepTime(categorized, 7);
        if(sleepTimeSegment.isPresent()) {
            extraSegments.add(sleepTimeSegment.get());
        }


        // add partner movement data, check if there's a partner
        final Optional<Long> optionalPartnerAccountId = this.deviceDAO.getPartnerAccountId(accessToken.accountId);
        if (optionalPartnerAccountId.isPresent()) {
            // get tracker motions for partner, query time is in UTC, not local_utc
            final DateTime startTime;
            if (sleepTimeSegment.isPresent()) {
                startTime = new DateTime(sleepTimeSegment.get().timestamp, DateTimeZone.UTC);
            } else {
                startTime = new DateTime(segments.get(0).timestamp, DateTimeZone.UTC);
            }
            final DateTime endTime = new DateTime(segments.get(segments.size() - 1).timestamp, DateTimeZone.UTC);
            final List<TrackerMotion> partnerMotions = this.trackerMotionDAO.getBetween(optionalPartnerAccountId.get(), startTime, endTime);
            if (partnerMotions.size() > 0) {
                // use un-normalized data segments for comparison
                extraSegments.addAll(PartnerMotion.getPartnerData(segments, partnerMotions, threshold));
            }
        }

        // add sunrise data

        final Optional<DateTime> sunrise = sunData.sunrise(targetDate.plusDays(1).toString(DateTimeFormat.forPattern("yyyy-MM-dd"))); // day + 1
        if(sunrise.isPresent()) {
            final String sunriseMessage = Event.getMessage(Event.Type.SUNRISE, sunrise.get());

            final SleepSegment sunriseSegment = new SleepSegment(1L, sunrise.get().getMillis(), 0, 60, -1, Event.Type.SUNRISE.toString(), sunriseMessage, new ArrayList<SensorReading>());

            extraSegments.add(sunriseSegment);

            LOGGER.debug(sunriseMessage);
        }

        // TODO: add sound, light, temperature event segments


        // combine all segments
        if (extraSegments.size() > 0) {
            categorized = TimelineUtils.insertSegmentsWithPriority(extraSegments, categorized);
        }

        LOGGER.debug("Size of normalized = {}", categorized.size());

        final List<SleepSegment> mergedSegments = TimelineUtils.mergeConsecutiveSleepSegments(categorized, mergeThreshold);
        final SleepStats sleepStats = TimelineUtils.computeStats(mergedSegments);
        final List<SleepSegment> reversed = Lists.reverse(mergedSegments);

        final int userOffsetMillis = trackerMotions.get(0).offsetMillis;
        final Integer sleepScore = sleepScoreDAO.getSleepScoreForNight(accessToken.accountId, targetDate.withTimeAtStartOfDay(),
                userOffsetMillis, this.dateBucketPeriod, sleepLabelDAO);
        final String timeLineMessage = TimelineUtils.generateMessage(sleepStats);

        LOGGER.debug("Score for account_id = {} is {}", accessToken.accountId, sleepScore);


        final List<Insight> insights = TimelineUtils.generateRandomInsights(targetDate.getDayOfMonth());
        final Timeline timeline = new Timeline(sleepScore, timeLineMessage, date, reversed, insights);
        final List<Timeline> timelines = new ArrayList<>();
        timelines.add(timeline);

        return timelines;
    }
}
