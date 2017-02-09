package com.hello.suripu.app.resources.v1;

import com.amazonaws.AmazonServiceException;
import com.codahale.metrics.annotation.Timed;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.hello.suripu.core.actions.Action;
import com.hello.suripu.core.actions.ActionProcessor;
import com.hello.suripu.core.actions.ActionType;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.db.MergedUserInfoDynamoDB;
import com.hello.suripu.core.db.SensorsViewsDynamoDB;
import com.hello.suripu.core.models.Device;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.models.DeviceStatus;
import com.hello.suripu.core.models.PairingInfo;
import com.hello.suripu.core.models.UserInfo;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.core.pill.heartbeat.PillHeartBeat;
import com.hello.suripu.core.pill.heartbeat.PillHeartBeatDAODynamoDB;
import com.hello.suripu.core.util.PillColorUtil;
import com.hello.suripu.coredropwizard.oauth.AccessToken;
import com.hello.suripu.coredropwizard.oauth.Auth;
import com.hello.suripu.coredropwizard.oauth.ScopesAllowed;
import com.hello.suripu.coredropwizard.resources.BaseResource;
import com.librato.rollout.RolloutClient;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionIsolationLevel;
import org.skife.jdbi.v2.TransactionStatus;
import org.skife.jdbi.v2.exceptions.UnableToExecuteStatementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;

@Path("/v1/devices")
public class DeviceResources extends BaseResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceResources.class);

    private static final Integer PILL_BATTERY_ALERT_THRESHOLD = 10;

    private final DeviceDAO deviceDAO;
    private final MergedUserInfoDynamoDB mergedUserInfoDynamoDB;
    private final SensorsViewsDynamoDB sensorsViewsDynamoDB;
    private final PillHeartBeatDAODynamoDB pillHeartBeatDAODynamoDB;

    @Inject
    RolloutClient feature;

    @Inject
    ActionProcessor actionProcessor;

    public DeviceResources(final DeviceDAO deviceDAO,
                           final MergedUserInfoDynamoDB mergedUserInfoDynamoDB,
                           final SensorsViewsDynamoDB sensorsViewsDynamoDB,
                           final PillHeartBeatDAODynamoDB pillHeartBeatDAODynamoDB) {
        this.deviceDAO = deviceDAO;
        this.mergedUserInfoDynamoDB = mergedUserInfoDynamoDB;
        this.sensorsViewsDynamoDB = sensorsViewsDynamoDB;
        this.pillHeartBeatDAODynamoDB = pillHeartBeatDAODynamoDB;
    }

    @ScopesAllowed({OAuthScope.DEVICE_INFORMATION_READ})
    @GET
    @Timed
    @Path("/info")
    @Produces(MediaType.APPLICATION_JSON)
    public PairingInfo getPairedSensesByAccount(@Auth final AccessToken accessToken) {
        final Optional<DeviceAccountPair> optionalPair = deviceDAO.getMostRecentSensePairByAccountId(accessToken.accountId);
        if(!optionalPair.isPresent()) {
            LOGGER.warn("No sense paired for account = {}", accessToken.accountId);
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        final DeviceAccountPair pair = optionalPair.get();
        final ImmutableList<DeviceAccountPair> pairs = deviceDAO.getAccountIdsForDeviceId(pair.externalDeviceId);
        return PairingInfo.create(pair.externalDeviceId, pairs.size());
    }

    @ScopesAllowed({OAuthScope.DEVICE_INFORMATION_READ})
    @GET
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    public List<Device> getDevices(@Auth final AccessToken accessToken) {
        return getDevicesByAccountId(accessToken.accountId);
    }

    @ScopesAllowed({OAuthScope.DEVICE_INFORMATION_WRITE})
    @DELETE
    @Timed
    @Path("/pill/{pill_id}")
    public void unregisterPill(@Auth final AccessToken accessToken,
                               @PathParam("pill_id") String externalPillId) {
        final Integer numRows = deviceDAO.deletePillPairing(externalPillId, accessToken.accountId);
        if(numRows == 0) {
            LOGGER.warn("Did not find active pill to unregister");
        }

        final List<DeviceAccountPair> sensePairedWithAccount = this.deviceDAO.getSensesForAccountId(accessToken.accountId);
        if(sensePairedWithAccount.size() == 0){
            LOGGER.error("No sense paired with account {}", accessToken.accountId);
            return;
        }

        final String senseId = sensePairedWithAccount.get(0).externalDeviceId;

        try {
            this.mergedUserInfoDynamoDB.deletePillColor(senseId, accessToken.accountId, externalPillId);
            this.actionProcessor.add(new Action(accessToken.accountId, ActionType.PILL_UNPAIR, Optional.of(externalPillId), DateTime.now(DateTimeZone.UTC), Optional.absent()));
        }catch (Exception ex){
            LOGGER.error("Delete pill {}'s color from user info table for sense {} and account {} failed: {}",
                    externalPillId,
                    senseId,
                    accessToken.accountId,
                    ex.getMessage());
        }
    }

    @ScopesAllowed({OAuthScope.DEVICE_INFORMATION_WRITE})
    @DELETE
    @Timed
    @Path("/sense/{sense_id}")
    public void unregisterSense(@Auth final AccessToken accessToken,
                               @PathParam("sense_id") String senseId) {
        final Integer numRows = deviceDAO.deleteSensePairing(senseId, accessToken.accountId);
        final Optional<UserInfo> alarmInfoOptional = this.mergedUserInfoDynamoDB.unlinkAccountToDevice(accessToken.accountId, senseId);

        // WARNING: Shall we throw error if the dynamoDB unlink fail?
        if(numRows == 0) {
            LOGGER.warn("Did not find active sense to unregister");
        } else {
            this.actionProcessor.add(new Action(accessToken.accountId, ActionType.SENSE_UNPAIR, Optional.of(senseId), DateTime.now(DateTimeZone.UTC), Optional.absent()));
        }

        if(!alarmInfoOptional.isPresent()){
            LOGGER.warn("Cannot find device {} account {} pair in merge info table.", senseId, accessToken.accountId);
        }
    }

    @ScopesAllowed({OAuthScope.DEVICE_INFORMATION_WRITE})
    @DELETE
    @Timed
    @Path("/sense/{sense_id}/all")
    public void unregisterSenseByUser(@Auth final AccessToken accessToken,
                                      @PathParam("sense_id") final String senseId) {
        final List<UserInfo> pairedUsers = mergedUserInfoDynamoDB.getInfo(senseId);

        try {
            this.deviceDAO.inTransaction(TransactionIsolationLevel.SERIALIZABLE, new Transaction<Void, DeviceDAO>() {
                @Override
                public Void inTransaction(final DeviceDAO transactional, final TransactionStatus status) throws Exception {
                    final Integer pillDeleted = transactional.deletePillPairingByAccount(accessToken.accountId);
                    LOGGER.info("Factory reset delete {} Pills linked to account {}", pillDeleted, accessToken.accountId);

                    final Integer accountUnlinked = transactional.unlinkAllAccountsPairedToSense(senseId);
                    LOGGER.info("Factory reset delete {} accounts linked to Sense {}", accountUnlinked, accessToken.accountId);

                    for (final UserInfo userInfo : pairedUsers) {
                        try {
                            mergedUserInfoDynamoDB.unlinkAccountToDevice(userInfo.accountId, userInfo.deviceId);
                        } catch (AmazonServiceException awsEx) {
                            LOGGER.error("Failed to unlink account {} from Sense {} in merge user info. error {}",
                                    userInfo.accountId,
                                    userInfo.deviceId,
                                    awsEx.getErrorMessage());
                        }
                    }

                    return null;
                }
            });
        }catch (UnableToExecuteStatementException sqlExp){
            LOGGER.error("Failed to factory reset Sense {}, error {}", senseId, sqlExp.getMessage());
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        this.actionProcessor.add(new Action(accessToken.accountId, ActionType.FACTORY_RESET_UNPAIR, Optional.of(senseId), DateTime.now(DateTimeZone.UTC), Optional.absent()));
    }


    public static Device.Color getPillColor(final List<UserInfo> userInfoList, final Long accountId)  {
        // mutable state. argh
        Device.Color pillColor = Device.Color.BLUE;

        for(final UserInfo userInfo : userInfoList) {
            if(!accountId.equals(userInfo.accountId) || !userInfo.pillColor.isPresent()) {
                continue;
            }
            pillColor = PillColorUtil.displayDeviceColor(userInfo.pillColor.get().getPillColor());
        }

        return pillColor;
    }

    private List<Device> getDevicesByAccountId(final Long accountId) {
        // TODO: make asynchronous calls to grab Pills + Senses if the following is too slow
        final ImmutableList<DeviceAccountPair> senses = deviceDAO.getSensesForAccountId(accountId);
        final ImmutableList<DeviceAccountPair> pills = deviceDAO.getPillsForAccountId(accountId);
        final List<Device> devices = Lists.newArrayList();

        final List<UserInfo> userInfoList= Lists.newArrayList();
        if(!senses.isEmpty()) {
            userInfoList.addAll(mergedUserInfoDynamoDB.getInfo(senses.get(0).externalDeviceId));
        }else{
            return Collections.EMPTY_LIST;
        }


        final Device.Color pillColor = getPillColor(userInfoList, accountId);

        // TODO: device state will always be normal for now until more information is provided by the device



        for (final DeviceAccountPair sense : senses) {
            final Optional<DeviceStatus> senseStatusOptional = sensorsViewsDynamoDB.senseStatus(sense.externalDeviceId, sense.accountId, sense.internalDeviceId);
            devices.add(senseDeviceStatusToSenseDevice(sense, senseStatusOptional));
        }

        devices.addAll(pillStatuses(pills, pillColor, accountId));
        return devices;
    }


    /**
     * Helper to convert DeviceStatus to Device object for Sense
     * @param pair
     * @param deviceStatusOptional
     * @return
     */
    private Device senseDeviceStatusToSenseDevice(final DeviceAccountPair pair, final Optional<DeviceStatus> deviceStatusOptional) {
        if(deviceStatusOptional.isPresent()) {
            return new Device(Device.Type.SENSE, pair.externalDeviceId, Device.State.NORMAL, deviceStatusOptional.get().firmwareVersion, deviceStatusOptional.get().lastSeen, Device.Color.BLACK);
        }

        return  new Device(Device.Type.SENSE, pair.externalDeviceId, Device.State.UNKNOWN, "-", null, Device.Color.BLACK);


    }

    @VisibleForTesting
    public List<Device> getDevices(final Long accountId) {
        return getDevicesByAccountId(accountId);
    }


    /**
     * Fetches pill status from datastores and converts it to Device object
     * @param pills
     * @param pillColor
     * @param accountId
     * @return  List of devices
     */
    private List<Device> pillStatuses(final List<DeviceAccountPair> pills, final Device.Color pillColor, final Long accountId) {
        final List<Device> devices = Lists.newArrayList();

        for (final DeviceAccountPair pill : pills) {
            final Optional<PillHeartBeat> pillHeartBeatOptional = pillHeartBeatDAODynamoDB.get(pill.externalDeviceId);
            if(pillHeartBeatOptional.isPresent()) {
                final PillHeartBeat pillHeartBeat = pillHeartBeatOptional.get();
                final Device.State state = pillState(pillHeartBeat.batteryLevel);
                devices.add(new Device(Device.Type.PILL, pill.externalDeviceId, state, String.valueOf(pillHeartBeat.firmwareVersion), pillHeartBeat.createdAtUTC, pillColor));
                continue;
            }

            LOGGER.debug("No pill status found for pill_id = {} ({}) for account: {}", pill.externalDeviceId, pill.internalDeviceId, pill.accountId);
            devices.add(new Device(Device.Type.PILL, pill.externalDeviceId, Device.State.UNKNOWN, null, null, pillColor));
        }

        return devices;
    }

    private Device.State pillState(int batteryLevel) {
        return (batteryLevel <= PILL_BATTERY_ALERT_THRESHOLD) ? Device.State.LOW_BATTERY : Device.State.NORMAL;
    }
}
