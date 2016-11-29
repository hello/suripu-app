package com.hello.suripu.app.v2;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;

import com.hello.suripu.core.alerts.Alert;
import com.hello.suripu.core.alerts.AlertsDAO;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.firmware.HardwareVersion;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.core.sense.metadata.SenseMetadata;
import com.hello.suripu.core.sense.metadata.SenseMetadataDAO;
import com.hello.suripu.core.sense.voice.VoiceMetadata;
import com.hello.suripu.core.sense.voice.VoiceMetadataDAO;
import com.hello.suripu.coredropwizard.oauth.AccessToken;
import com.hello.suripu.coredropwizard.oauth.Auth;
import com.hello.suripu.coredropwizard.oauth.ScopesAllowed;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/v2/alerts")
public class AlertsResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlertsResource.class);


    private final AlertsDAO alertsDAO;
    private final VoiceMetadataDAO voiceMetadataDAO;
    private final DeviceDAO deviceDAO;
    private final SenseMetadataDAO senseMetadataDAO;

    public AlertsResource(final AlertsDAO alertsDAO,
                          final VoiceMetadataDAO voiceMetadataDAO,
                          final DeviceDAO deviceDAO,
                          final SenseMetadataDAO senseMetadataDAO) {
        this.alertsDAO = alertsDAO;
        this.voiceMetadataDAO = voiceMetadataDAO;
        this.deviceDAO = deviceDAO;
        this.senseMetadataDAO = senseMetadataDAO;
    }

    @ScopesAllowed({OAuthScope.ALERTS_READ})
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Alert> get(@Auth AccessToken accessToken) {

        final List<DeviceAccountPair> pairs = deviceDAO.getSensesForAccountId(accessToken.accountId);

        if(!pairs.isEmpty()) {
            final DeviceAccountPair pair = pairs.get(0);
            final VoiceMetadata voiceMetadata = voiceMetadataDAO.get(pair.externalDeviceId, accessToken.accountId, accessToken.accountId);
            final SenseMetadata senseMetadata = senseMetadataDAO.get(pair.externalDeviceId);

            if(voiceMetadata.muted() && HardwareVersion.SENSE_ONE_FIVE.equals(senseMetadata.hardwareVersion())) {
                LOGGER.debug("action=show-mute-alarm sense_id={} account_id={}", pair.externalDeviceId, accessToken.accountId);
                return Lists.newArrayList(Alert.muted(accessToken.accountId, DateTime.now()));
            }
        }

        final Optional<Alert> alertOptional = alertsDAO.mostRecentNotSeen(accessToken.accountId);
        if(!alertOptional.isPresent()) {
            return Lists.newArrayList();
        }
        alertsDAO.seen(alertOptional.get().id());
        return Lists.newArrayList(alertOptional.get());
    }
}
