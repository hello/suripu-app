package com.hello.suripu.app.alarms;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;

import com.amazonaws.services.s3.AmazonS3;
import com.codahale.metrics.annotation.Timed;
import com.hello.suripu.app.utils.ExpansionUtils;
import com.hello.suripu.core.alarm.AlarmConflictException;
import com.hello.suripu.core.alarm.AlarmProcessor;
import com.hello.suripu.core.alarm.DuplicateSmartAlarmException;
import com.hello.suripu.core.alarm.GetAlarmException;
import com.hello.suripu.core.alarm.InvalidAlarmException;
import com.hello.suripu.core.alarm.InvalidTimezoneException;
import com.hello.suripu.core.alarm.InvalidUserException;
import com.hello.suripu.core.alarm.TooManyAlarmsException;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.models.Alarm;
import com.hello.suripu.core.models.AlarmSound;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.core.translations.English;
import com.hello.suripu.core.util.AlarmUtils;
import com.hello.suripu.core.util.JsonError;
import com.hello.suripu.coredropwizard.oauth.AccessToken;
import com.hello.suripu.coredropwizard.oauth.Auth;
import com.hello.suripu.coredropwizard.oauth.ScopesAllowed;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import is.hello.gaibu.core.models.Expansion;
import is.hello.gaibu.core.stores.ExpansionStore;

@Path("/v2/alarms")
public class AlarmGroupsResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlarmGroupsResource.class);
    private final DeviceDAO deviceDAO;
    private final AmazonS3 amazonS3;
    private final AlarmProcessor alarmProcessor;
    private final ExpansionStore<Expansion> expansionStore;

    public AlarmGroupsResource(final DeviceDAO deviceDAO,
                               final AmazonS3 amazonS3,
                               final AlarmProcessor alarmProcessor,
                               final ExpansionStore<Expansion> expansionStore){
        this.deviceDAO = deviceDAO;
        this.amazonS3 = amazonS3;
        this.alarmProcessor = alarmProcessor;
        this.expansionStore = expansionStore;
    }

    @ScopesAllowed({OAuthScope.ALARM_READ})
    @Timed
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public AlarmGroups getAlarms(@Auth final AccessToken token){

        final List<DeviceAccountPair> deviceAccountMap = this.deviceDAO.getSensesForAccountId(token.accountId);
        if(deviceAccountMap.size() == 0){
            LOGGER.error("error=get-alarm-fail reason=no-paired-sense account_id={}", token.accountId);
            throw new WebApplicationException(Response.status(Response.Status.PRECONDITION_FAILED).entity(
                    new JsonError(Response.Status.PRECONDITION_FAILED.getStatusCode(),
                            "Please make sure your Sense is paired to your account before setting your alarm.")).build());
        }

        try {
            final List<Alarm> alarms = alarmProcessor.getAlarms(token.accountId, deviceAccountMap.get(0).externalDeviceId);
            LOGGER.debug("action=get-alarms account_id={} alarm_size={}", token.accountId, alarms.size());

            final AlarmGroups group = AlarmGroups.create(new ArrayList<>(), new ArrayList<>(), alarms);
            return group;

        } catch (InvalidTimezoneException timezoneException) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).entity(
                    new JsonError(Response.Status.BAD_REQUEST.getStatusCode(),
                            timezoneException.getMessage())).build());

        } catch (InvalidAlarmException alarmException) {
            throw new WebApplicationException(Response.status(Response.Status.CONFLICT).entity(
                    new JsonError(Response.Status.CONFLICT.getStatusCode(),
                            alarmException.getMessage())).build());

        } catch (GetAlarmException getAlarmException) {
            throw new WebApplicationException(Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(
                    new JsonError(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                            "Please try again.")).build());

        }
    }


    @ScopesAllowed({OAuthScope.ALARM_WRITE})
    @Timed
    @POST
    @Path("/{client_time_utc}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public AlarmGroups setAlarms(@Auth final AccessToken token,
                                 @PathParam("client_time_utc") long clientTime,
                                 final AlarmGroups group){

        final DateTime now = DateTime.now();
        if(!AlarmUtils.isWithinReasonableBounds(now, clientTime, 50000)) {
            LOGGER.error("error=set-alarm-fail reason=client-time-off account_id={} client_now={} actual_now={}", token.accountId, clientTime, now);
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).entity(
                    new JsonError(Response.Status.BAD_REQUEST.getStatusCode(), English.ERROR_CLOCK_OUT_OF_SYNC)).build()
            );
        }

        final Optional<DeviceAccountPair> deviceAccountPair = this.deviceDAO.getMostRecentSensePairByAccountId(token.accountId);
        if(!deviceAccountPair.isPresent()){
            LOGGER.error("error=set-alarm-fail reason=no-paired-sense account_id={}", token.accountId);
            throw new WebApplicationException(Response.Status.FORBIDDEN);
        }

        // Only update alarms in the account that linked with the most recent sense.
        final String senseId = deviceAccountPair.get().externalDeviceId;

        final List<Alarm> alarms = new ArrayList<>();
        alarms.addAll(group.classic());
        alarms.addAll(ExpansionUtils.updateExpansionAlarms(expansionStore, group.voice(), token.accountId));
        alarms.addAll(ExpansionUtils.updateExpansionAlarms(expansionStore, group.expansions(), token.accountId));

        try {
            alarmProcessor.setAlarms(token.accountId, senseId, alarms);

        } catch (InvalidUserException | InvalidTimezoneException userRelatedException) {
            LOGGER.error("error=set-alarm-fail reason=user-info-missing err_msg={} return=BAD_REQUEST", userRelatedException.getMessage());
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).build());

        } catch (AlarmConflictException alarmConflictException) {
            throw new WebApplicationException(Response.status(Response.Status.CONFLICT).entity(
                    new JsonError(Response.Status.CONFLICT.getStatusCode(),
                            alarmConflictException.getMessage())).build());

        } catch (DuplicateSmartAlarmException duplicateException) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).entity(
                    new JsonError(Response.Status.BAD_REQUEST.getStatusCode(),
                            duplicateException.getMessage())).build());

        } catch (TooManyAlarmsException | GetAlarmException alarmException) {
            throw new WebApplicationException(Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(
                    new JsonError(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                            alarmException.getMessage())).build());
        }

        return group;
    }

    // TODO: extract this logic to not have to repeat it across API versions;
    @ScopesAllowed({OAuthScope.ALARM_READ})
    @Timed
    @GET
    @Path("/sounds")
    @Produces(MediaType.APPLICATION_JSON)
    public List<AlarmSound> getAlarmSounds(@Auth final AccessToken accessToken) {
        final List<AlarmSound> alarmSounds = Lists.newArrayList();


        // Note: this is the order in which they appear in the app.
        final List<SoundTuple> sounds = Lists.newArrayList(
                new SoundTuple("Dusk", 5),
                new SoundTuple("Pulse", 4),
                new SoundTuple("Lilt", 6),
                new SoundTuple("Bounce", 7),
                new SoundTuple("Celebration", 8),
                new SoundTuple("Milky Way", 9),
                new SoundTuple("Waves", 10),
                new SoundTuple("Lights", 11),
                new SoundTuple("Echo", 12),
                new SoundTuple("Drops", 13),
                new SoundTuple("Twinkle", 14),
                new SoundTuple("Silver", 15),
                new SoundTuple("Highlights", 16),
                new SoundTuple("Ripple", 17),
                new SoundTuple("Sway", 18)
        );

        for(final SoundTuple tuple: sounds) {
            final URL url = amazonS3.generatePresignedUrl("hello-audio", String.format("ringtones/%s.mp3", tuple.displayName), DateTime.now().plusWeeks(1).toDate());
            final AlarmSound sound = new AlarmSound(tuple.id, tuple.displayName, url.toExternalForm());
            alarmSounds.add(sound);
        }

        return alarmSounds;
    }

    private static class SoundTuple {
        public final String displayName;
        public final Integer id;

        public SoundTuple(final String displayName, final Integer id) {
            this.id = id;
            this.displayName = displayName;
        }
    }
}
