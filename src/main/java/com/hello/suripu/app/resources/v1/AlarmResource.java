package com.hello.suripu.app.resources.v1;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.hello.suripu.core.db.AlarmDAODynamoDB;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.db.MergedAlarmInfoDynamoDB;
import com.hello.suripu.core.models.Alarm;
import com.hello.suripu.core.models.AlarmInfo;
import com.hello.suripu.core.models.AlarmSound;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.oauth.AccessToken;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.core.oauth.Scope;
import com.yammer.metrics.annotation.Timed;
import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by pangwu on 9/17/14.
 */
@Path("/v1/alarms")
public class AlarmResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlarmResource.class);
    private final AlarmDAODynamoDB alarmDAODynamoDB;
    private final MergedAlarmInfoDynamoDB mergedAlarmInfoDynamoDB;
    private final DeviceDAO deviceDAO;
    private final AmazonS3 amazonS3;

    public AlarmResource(final AlarmDAODynamoDB alarmDAODynamoDB,
                         final MergedAlarmInfoDynamoDB mergedAlarmInfoDynamoDB,
                         final DeviceDAO deviceDAO,
                         final AmazonS3 amazonS3){
        this.alarmDAODynamoDB = alarmDAODynamoDB;
        this.mergedAlarmInfoDynamoDB = mergedAlarmInfoDynamoDB;
        this.deviceDAO = deviceDAO;
        this.amazonS3 = amazonS3;
    }

    @Timed
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Alarm> getAlarms(@Scope({OAuthScope.ALARM_READ}) final AccessToken token){
        LOGGER.debug("Before getting device account map from account_id");
        final List<DeviceAccountPair> deviceAccountMap = this.deviceDAO.getDeviceAccountMapFromAccountId(token.accountId);
        if(deviceAccountMap.size() == 0){
            LOGGER.error("User {} tries to retrieve alarm without paired with a Morpheus.", token.accountId);
            throw new WebApplicationException(Response.Status.BAD_REQUEST);
        }

        try {
            LOGGER.debug("Before getting device account map from account_id");
            final Optional<AlarmInfo> alarmInfoOptional = this.mergedAlarmInfoDynamoDB.getInfo(deviceAccountMap.get(0).externalDeviceId, token.accountId);
            LOGGER.debug("Fetched alarm info optional");

            if(!alarmInfoOptional.isPresent()){
                LOGGER.warn("Merge alarm info table doesn't have record for device {}, account {}.", deviceAccountMap.get(0).externalDeviceId, token.accountId);

                // At account creation, the merged table doesn't have any alarm info, so let's create an empty one
                mergedAlarmInfoDynamoDB.setAlarms(deviceAccountMap.get(0).externalDeviceId, token.accountId, Collections.EMPTY_LIST);
                LOGGER.warn("Saved empty alarm info for device {} and account {}.", deviceAccountMap.get(0).externalDeviceId, token.accountId);
//                throw new WebApplicationException(Response.Status.BAD_REQUEST);
                return Collections.emptyList();
            }


            final AlarmInfo alarmInfo = alarmInfoOptional.get();
            if(!alarmInfo.timeZone.isPresent()){
                LOGGER.error("User {} tries to get alarm without having a time zone.", token.accountId);
                throw new WebApplicationException(Response.Status.BAD_REQUEST);
            }

            final DateTimeZone userTimeZone = alarmInfo.timeZone.get();
            return Alarm.Utils.disableExpiredNoneRepeatedAlarms(alarmInfo.alarmList, DateTime.now().getMillis(), userTimeZone);
        }catch (AmazonServiceException awsException){
            LOGGER.error("Aws failed when user {} tries to get alarms.", token.accountId);
            throw new WebApplicationException(Response.status(Response.Status.INTERNAL_SERVER_ERROR).build());
        }

    }


    @Timed
    @POST
    @Path("/{client_time_utc}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public void setAlarms(@Scope({OAuthScope.ALARM_WRITE}) final AccessToken token,
                          @PathParam("client_time_utc") long clientTime,
                          final List<Alarm> alarms){

        if(Math.abs(DateTime.now().getMillis() - clientTime) > DateTimeConstants.MILLIS_PER_MINUTE){
            LOGGER.error("account_id {} set alarm failed, client time too off.", token.accountId);
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).build());
        }

        if(!Alarm.Utils.isValidSmartAlarms(alarms)){
            LOGGER.error("account id {} set alarm failed, two alarm in the same day.", token.accountId);
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).build());
        }

        final List<DeviceAccountPair> deviceAccountMap = this.deviceDAO.getDeviceAccountMapFromAccountId(token.accountId);
        if(deviceAccountMap.size() == 0){
            LOGGER.error("User tries to set alarm without connected to a Morpheus.", token.accountId);
            throw new WebApplicationException(Response.Status.FORBIDDEN);
        }

        for(final DeviceAccountPair deviceAccountPair:deviceAccountMap){
            try {
                this.mergedAlarmInfoDynamoDB.setAlarms(deviceAccountPair.externalDeviceId, token.accountId, alarms);
                this.alarmDAODynamoDB.setAlarms(token.accountId, alarms);
            }catch (AmazonServiceException awsException){
                LOGGER.error("Aws failed when user {} tries to get alarms.", token.accountId);
                throw new WebApplicationException(Response.status(Response.Status.INTERNAL_SERVER_ERROR).build());
            }
        }
    }


    @Timed
    @GET
    @Path("/sounds")
    @Produces(MediaType.APPLICATION_JSON)
    public List<AlarmSound> getAlarmSounds(@Scope(OAuthScope.ALARM_READ) final AccessToken accessToken) {
        final List<AlarmSound> alarmSounds = new ArrayList<>();
        final List<String> fileNames = Lists.newArrayList(
                "102914_SENSE_DIGITAL_1.1",
                "102914_SENSE_DIGITAL_2.1",
                "102914_SENSE_DIGITAL_3.1",
                "102914_SENSE_DIGITAL_4.1",
                "102914_SENSE_DIGITAL_5.1",
                "102914_SENSE_ORGANIC_2.1",
                "102914_SENSE_ORGANIC_3.1",
                "102914_SENSE_NEW_1",
                "102914_SENSE_NEW_2",
                "102914_SENSE_NEW_3",
                "102914_SENSE_NEW_4",
                "102914_SENSE_NEW_5",
                "102914_SENSE_NEW_6",
                "102914_SENSE_NEW_7",
                "102914_SENSE_NEW_8",
                "102914_SENSE_NEW_9"
        );

        int i = 1;
        for(String fileName : fileNames) {
            final URL url = amazonS3.generatePresignedUrl("hello-audio", String.format("ringtones/%s.mp3", fileName), DateTime.now().plusWeeks(1).toDate());
            final AlarmSound sound = new AlarmSound(i, fileName, url.toExternalForm());
            alarmSounds.add(sound);
            i++;
        }

        return alarmSounds;
    }
}