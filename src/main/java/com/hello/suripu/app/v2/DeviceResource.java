package com.hello.suripu.app.v2;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Optional;
import com.hello.suripu.app.modules.AppFeatureFlipper;
import com.hello.suripu.core.actions.Action;
import com.hello.suripu.core.actions.ActionProcessor;
import com.hello.suripu.core.actions.ActionType;
import com.hello.suripu.core.db.AccountDAO;
import com.hello.suripu.core.firmware.HardwareVersion;
import com.hello.suripu.core.messeji.MessejiApi;
import com.hello.suripu.core.messeji.Sender;
import com.hello.suripu.core.models.Account;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.models.DeviceStatus;
import com.hello.suripu.core.models.PairingInfo;
import com.hello.suripu.core.models.WifiInfo;
import com.hello.suripu.core.models.device.v2.DeviceProcessor;
import com.hello.suripu.core.models.device.v2.DeviceQueryInfo;
import com.hello.suripu.core.models.device.v2.Devices;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.core.sense.metadata.SenseMetadata;
import com.hello.suripu.core.sense.metadata.SenseMetadataDAO;
import com.hello.suripu.core.sense.voice.VoiceMetadata;
import com.hello.suripu.core.sense.voice.VoiceMetadataDAO;
import com.hello.suripu.core.swap.IntentResult;
import com.hello.suripu.core.swap.Request;
import com.hello.suripu.core.swap.Status;
import com.hello.suripu.core.swap.Swapper;
import com.hello.suripu.core.util.JsonError;
import com.hello.suripu.coredropwizard.clients.MessejiClient;
import com.hello.suripu.coredropwizard.oauth.AccessToken;
import com.hello.suripu.coredropwizard.oauth.Auth;
import com.hello.suripu.coredropwizard.oauth.ScopesAllowed;
import com.hello.suripu.coredropwizard.resources.BaseResource;
import com.librato.rollout.RolloutClient;
import io.dropwizard.jersey.PATCH;
import is.hello.gaibu.core.models.ExternalToken;
import is.hello.gaibu.core.stores.ExternalOAuthTokenStore;
import org.hibernate.validator.constraints.NotEmpty;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
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
import java.util.Collections;
import java.util.Map;

@Path("/v2/devices")
public class DeviceResource extends BaseResource {

    @Inject
    RolloutClient flipper;

    @Inject
    ActionProcessor actionProcessor;

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceResource.class);

    private final DeviceProcessor deviceProcessor;
    private final Swapper swapper;
    private final AccountDAO accountDAO;
    private final SenseMetadataDAO senseMetadataDAO;
    private final VoiceMetadataDAO voiceMetadataDAO;
    private final MessejiApi messejiClient;
    private final ExternalOAuthTokenStore<ExternalToken> externalTokenStore;

    public DeviceResource(final DeviceProcessor deviceProcessor,
                          final Swapper swapper,
                          final AccountDAO accountDAO,
                          final SenseMetadataDAO senseMetadataDAO,
                          final VoiceMetadataDAO voiceMetadataDAO,
                          final MessejiClient messejiClient,
                          final ExternalOAuthTokenStore<ExternalToken> externalTokenStore) {
        this.deviceProcessor = deviceProcessor;
        this.swapper = swapper;
        this.accountDAO = accountDAO;
        this.senseMetadataDAO = senseMetadataDAO;
        this.voiceMetadataDAO = voiceMetadataDAO;
        this.messejiClient = messejiClient;
        this.externalTokenStore = externalTokenStore;
    }

    @ScopesAllowed({OAuthScope.DEVICE_INFORMATION_READ})
    @GET
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    public Devices getDevices(@Auth final AccessToken accessToken) {



        final boolean lowBatteryOverride = flipper.userFeatureActive(AppFeatureFlipper.LOW_BATTERY_ALERT_OVERRIDE, accessToken.accountId, Collections.<String>emptyList());
        if(lowBatteryOverride) {
            final Optional<Account> accountOptional = accountDAO.getById(accessToken.accountId);
            if(!accountOptional.isPresent()) {
                throw new WebApplicationException(Response.Status.NOT_FOUND);
            }

            final DeviceQueryInfo deviceQueryInfo = DeviceQueryInfo.create(
                    accessToken.accountId,
                    this.isSenseLastSeenDynamoDBReadEnabled(accessToken.accountId),
                    this.isSensorsDBUnavailable(accessToken.accountId),
                    accountOptional.get()
            );
            return deviceProcessor.getAllDevices(deviceQueryInfo);
        }

        final DeviceQueryInfo deviceQueryInfo = DeviceQueryInfo.create(
                accessToken.accountId,
                this.isSenseLastSeenDynamoDBReadEnabled(accessToken.accountId),
                this.isSensorsDBUnavailable(accessToken.accountId)
        );
        return deviceProcessor.getAllDevices(deviceQueryInfo);

    }

    @ScopesAllowed({OAuthScope.DEVICE_INFORMATION_READ})
    @GET
    @Timed
    @Path("/info")
    @Produces(MediaType.APPLICATION_JSON)
    public PairingInfo getPairingInfo(@Auth final AccessToken accessToken) {

        final Optional<PairingInfo> pairingInfoOptional = deviceProcessor.getPairingInfo(accessToken.accountId);
        if (!pairingInfoOptional.isPresent()) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        return pairingInfoOptional.get();
    }


    @ScopesAllowed({OAuthScope.DEVICE_INFORMATION_WRITE})
    @DELETE
    @Timed
    @Path("/pill/{pill_id}")
    public Response unregisterPill(@Auth final AccessToken accessToken,
                                   @PathParam("pill_id") final String pillId) {

        deviceProcessor.unregisterPill(accessToken.accountId, pillId);
        this.actionProcessor.add(new Action(accessToken.accountId, ActionType.PILL_UNPAIR, Optional.of(pillId), DateTime.now(DateTimeZone.UTC), Optional.absent()));

        return Response.noContent().build();
    }

    @ScopesAllowed({OAuthScope.DEVICE_INFORMATION_WRITE})
    @DELETE
    @Timed
    @Path("/sense/{sense_id}")
    public Response unregisterSense(@Auth final AccessToken accessToken,
                                    @PathParam("sense_id") final String senseId) {
        deviceProcessor.unregisterSense(accessToken.accountId, senseId);
        this.actionProcessor.add(new Action(accessToken.accountId, ActionType.SENSE_UNPAIR, Optional.of(senseId), DateTime.now(DateTimeZone.UTC), Optional.absent()));

        return Response.noContent().build();
    }

    @ScopesAllowed({OAuthScope.DEVICE_INFORMATION_WRITE})
    @DELETE
    @Timed
    @Path("/sense/{sense_id}/all")
    public Response factoryReset(@Auth final AccessToken accessToken,
                                 @PathParam("sense_id") final String senseId) {
        deviceProcessor.factoryReset(accessToken.accountId, senseId);
        externalTokenStore.disableAllByDeviceId(senseId);
        this.actionProcessor.add(new Action(accessToken.accountId, ActionType.FACTORY_RESET_UNPAIR, Optional.of(senseId), DateTime.now(DateTimeZone.UTC), Optional.absent()));
        return Response.noContent().build();
    }

    @ScopesAllowed({OAuthScope.DEVICE_INFORMATION_WRITE})
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/wifi_info")
    public Response updateWifiInfo(@Auth final AccessToken accessToken,
                                   @Valid final WifiInfo wifiInfo){
        try {
            final Boolean hasUpserted = deviceProcessor.upsertWifiInfo(accessToken.accountId, wifiInfo);
            if (!hasUpserted) {
                throw new WebApplicationException(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(new JsonError(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                                String.format("Failed to upsert wifi info for sense %s", wifiInfo.senseId))).build());
            }

        } catch (DeviceProcessor.InvalidWifiInfoException iwe) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                    .entity(new JsonError(Response.Status.BAD_REQUEST.getStatusCode(), iwe.getMessage())).build());
        }

        return Response.noContent().build();
    }

    @ScopesAllowed({OAuthScope.DEVICE_INFORMATION_WRITE})
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/swap")
    public com.hello.suripu.core.swap.Response swap(@Auth final AccessToken accessToken,
                           @Valid final Request swapRequest){

        LOGGER.info("action=swap-request sense_id={} account_id={}", swapRequest.senseId(), accessToken.accountId);
        // TODO: check that swaprequest.senseId() is provisioned before authorizing the swap.
        final IntentResult result = swapper.eligible(accessToken.accountId, swapRequest.senseId());
        if(result.intent().isPresent()) {
            swapper.create(result.intent().get());
            return com.hello.suripu.core.swap.Response.create(Status.OK);
        }

        return com.hello.suripu.core.swap.Response.create(result.status());
    }

    @ScopesAllowed({OAuthScope.DEVICE_INFORMATION_WRITE})
    @PATCH
    @Path("/sense/{sense_id}/voice")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateVoice(@Auth final AccessToken accessToken, @NotEmpty Map<VoiceMetadata.UpdateType, Object> properties, @PathParam("sense_id") String senseId) {

        final boolean legitPairing = deviceProcessor.isPairedTo(accessToken.accountId, senseId);
        if(!legitPairing) {
            LOGGER.error("action=update-voice-metadata error=sense-not-paired-to-account account_id={} sense_id={}", accessToken.accountId, senseId);
            throw new WebApplicationException(404);
        }

        for(final VoiceMetadata.UpdateType type : properties.keySet()) {
            switch (type) {
                case MUTED:
                    boolean shouldMute = (boolean) properties.get(type);
                    final Optional<Long> id = messejiClient.mute(senseId, Sender.fromAccountId(accessToken.accountId), System.currentTimeMillis(), shouldMute);
                    if(id.isPresent()) {
                        LOGGER.info("mute={} sense_id={} account_id={}", shouldMute, senseId, accessToken.accountId);
                    } else {
                        LOGGER.warn("error=no-response-from-messeji should_mute={} account_id={} sense_id={}", shouldMute, accessToken.accountId, senseId);
                    }
                    break;
                case IS_PRIMARY_USER:
                    if((boolean) properties.get(type)) {
                        voiceMetadataDAO.updatePrimaryAccount(senseId, accessToken.accountId);
                    }
                    break;
                case VOLUME:
                    int volume = (int) properties.get(VoiceMetadata.UpdateType.VOLUME);
                    final Optional<Long> volumeId = messejiClient.setSystemVolume(senseId, Sender.fromAccountId(accessToken.accountId), System.currentTimeMillis(), volume);
                    if(volumeId.isPresent()) {
                        LOGGER.info("volume={} sense_id={} account_id={}", volume, senseId, accessToken.accountId);
                    } else {
                        LOGGER.warn("error=no-response-from-messeji volume={} account_id={} sense_id={}", volume, accessToken.accountId, senseId);
                    }
                    break;
            }
        }

        return Response.noContent().build();
    }

    @ScopesAllowed({OAuthScope.DEVICE_INFORMATION_READ})
    @GET
    @Path("/sense/{sense_id}/voice")
    @Produces(MediaType.APPLICATION_JSON)
    public VoiceMetadata voiceMetadata(@Auth final AccessToken accessToken, @PathParam("sense_id") String senseId) {
        final boolean legitPairing = deviceProcessor.isPairedTo(accessToken.accountId, senseId);
        if(!legitPairing) {
            LOGGER.error("action=get-voice-metadata error=sense-not-paired-to-account account_id={} sense_id={}", accessToken.accountId, senseId);
            throw new WebApplicationException(404);
        }

        // Checking for min fw version
        final DeviceAccountPair pair = new DeviceAccountPair(accessToken.accountId, 0L, senseId, DateTime.now());
        final Optional<DeviceStatus> deviceStatus = deviceProcessor.retrieveSenseStatus(pair);
        final int minFirmwareVersion = 6075; // rc3
        if(!deviceStatus.isPresent() || Integer.parseInt(deviceStatus.get().firmwareVersion, 16) < minFirmwareVersion) {
            LOGGER.warn("sense_id={} account_id={} action=voice-setting-not-available", senseId, accessToken.accountId);
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).entity(new JsonError(400, "Requires firmware update")).build());
        }

        final SenseMetadata senseMetadata = senseMetadataDAO.get(senseId);
        if(!HardwareVersion.SENSE_ONE_FIVE.equals(senseMetadata.hardwareVersion())) {
            LOGGER.error("action=get-voice-metadata error=sense-one sense_id={} account_id={}", senseId, accessToken.accountId);
            throw new WebApplicationException(400);
        }

        return deviceProcessor.voiceMetadata(senseId, accessToken.accountId);
    }
}
