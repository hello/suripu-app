package com.hello.suripu.app.resources.v1;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

import com.codahale.metrics.annotation.Timed;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.db.OTAHistoryDAODynamoDB;
import com.hello.suripu.core.db.ResponseCommandsDAODynamoDB;
import com.hello.suripu.core.db.SensorsViewsDynamoDB;
import com.hello.suripu.core.flipper.FeatureFlipper;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.models.DeviceData;
import com.hello.suripu.core.models.OTAHistory;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.coredw8.oauth.AccessToken;
import com.hello.suripu.coredw8.oauth.Auth;
import com.hello.suripu.coredw8.oauth.ScopesAllowed;
import com.hello.suripu.coredw8.resources.BaseResource;
import com.librato.rollout.RolloutClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/v1/ota")
public class OTAResource extends BaseResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(OTAResource.class);

    private final DeviceDAO deviceDAO;
    private final OTAHistoryDAODynamoDB otaHistoryDAO;
    private final ResponseCommandsDAODynamoDB responseCommandsDAODynamoDB;
    private final SensorsViewsDynamoDB sensorsViewsDynamoDB;

    @Inject
    RolloutClient feature;

    public OTAResource(final DeviceDAO deviceDAO,
                       final SensorsViewsDynamoDB sensorsViewsDynamoDB,
                       final OTAHistoryDAODynamoDB otaHistoryDAO,
                       final ResponseCommandsDAODynamoDB responseCommandsDAODynamoDB) {
        this.deviceDAO = deviceDAO;
        this.sensorsViewsDynamoDB = sensorsViewsDynamoDB;
        this.otaHistoryDAO = otaHistoryDAO;
        this.responseCommandsDAODynamoDB = responseCommandsDAODynamoDB;
    }

    @ScopesAllowed({OAuthScope.DEVICE_INFORMATION_READ})
    @GET
    @Timed
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    public OTAHistory.OTAStatus getLatestOTAStatus(@Auth final AccessToken accessToken) {
        final Optional<DeviceAccountPair> optionalPair = deviceDAO.getMostRecentSensePairByAccountId(accessToken.accountId);
        if(!optionalPair.isPresent()) {
            LOGGER.warn("No sense paired for account = {}", accessToken.accountId);
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        final DeviceAccountPair pair = optionalPair.get();
        final String deviceId = pair.externalDeviceId;

        final Optional<DeviceData> deviceDataOptional = sensorsViewsDynamoDB.lastSeen(pair.externalDeviceId, accessToken.accountId, pair.internalDeviceId);
        if(!deviceDataOptional.isPresent()) {
            LOGGER.error("error=no-device-data device_id={}", deviceId);
            throw new WebApplicationException(Response.Status.BAD_REQUEST);
        }

        final DeviceData data = deviceDataOptional.get();
        final Integer fwVersion = data.firmwareVersion;


        final Optional<OTAHistory> optionalOTAHistory = otaHistoryDAO.getLatest(pair.externalDeviceId);
        if(!optionalOTAHistory.isPresent()) {
            LOGGER.warn("No OTA History found for device_id={}", pair.externalDeviceId);
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        final OTAHistory history = optionalOTAHistory.get();

        if((!history.currentFWVersion.equals(fwVersion.toString())) && feature.deviceFeatureActive(FeatureFlipper.FW_VERSIONS_REQUIRING_UPDATE, fwVersion.toString(), Collections.EMPTY_LIST)) {
            return OTAHistory.OTAStatus.UPDATE_REQUIRED;
        }

        return optionalOTAHistory.get().otaStatus;
    }


    @ScopesAllowed({OAuthScope.DEVICE_INFORMATION_READ})
    @POST
    @Timed
    @Path("/request_ota")
    @Produces(MediaType.APPLICATION_JSON)
    public void setForcedOTAResponseCommand(@Auth final AccessToken accessToken) {

        final Optional<DeviceAccountPair> optionalPair = deviceDAO.getMostRecentSensePairByAccountId(accessToken.accountId);
        if(!optionalPair.isPresent()) {
            LOGGER.warn("No sense paired for account = {}", accessToken.accountId);
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        final DeviceAccountPair pair = optionalPair.get();
        final String deviceId = pair.externalDeviceId;

        final Optional<DeviceData> deviceDataOptional = sensorsViewsDynamoDB.lastSeen(pair.externalDeviceId, accessToken.accountId, pair.internalDeviceId);
        if(!deviceDataOptional.isPresent()) {
            LOGGER.error("error=no-device-data device_id={}", deviceId);
            throw new WebApplicationException(Response.Status.BAD_REQUEST);
        }

        final DeviceData data = deviceDataOptional.get();
        final Integer fwVersion = data.firmwareVersion;

        LOGGER.info("Resetting device: {} on FW Version: {}", deviceId, fwVersion);

        final Map<ResponseCommandsDAODynamoDB.ResponseCommand, String> issuedCommands = new ImmutableMap.Builder<ResponseCommandsDAODynamoDB.ResponseCommand, String>()
            .put(ResponseCommandsDAODynamoDB.ResponseCommand.FORCE_OTA, "true")
            .build();

        responseCommandsDAODynamoDB.insertResponseCommands(deviceId, fwVersion, issuedCommands);
    }
}
