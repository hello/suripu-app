package com.hello.suripu.app.v2;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.db.KeyStore;
import com.hello.suripu.core.firmware.HardwareVersion;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.models.DeviceKeyStoreRecord;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.coredw8.oauth.AccessToken;
import com.hello.suripu.coredw8.oauth.Auth;
import com.hello.suripu.coredw8.oauth.ScopesAllowed;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Map;

@Path("/v2/features")
public class UserFeaturesResource {

    private final DeviceDAO deviceDAO;
    private final KeyStore keyStore;

    public UserFeaturesResource(final DeviceDAO deviceDAO, final KeyStore keyStore) {
        this.deviceDAO = deviceDAO;
        this.keyStore = keyStore;
    }

    public enum UserFeatureName {
        VOICE;
    }

    @ScopesAllowed(OAuthScope.DEVICE_INFORMATION_READ)
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<UserFeatureName, Boolean> get(@Auth final AccessToken accessToken) {

        final Optional<DeviceAccountPair> deviceAccountPairOptional = deviceDAO.getMostRecentSensePairByAccountId(accessToken.accountId);
        if(!deviceAccountPairOptional.isPresent()) {
            return Maps.newHashMap();
        }

        final Optional<DeviceKeyStoreRecord> deviceKeyStoreRecord = keyStore.getKeyStoreRecord(deviceAccountPairOptional.get().externalDeviceId);
        if(!deviceKeyStoreRecord.isPresent()) {
            return Maps.newHashMap();
        }

        if(deviceKeyStoreRecord.get().hardwareVersion.equals(HardwareVersion.SENSE_ONE_FIVE)) {
            final ImmutableMap<UserFeatureName, Boolean> map = ImmutableMap.of(UserFeatureName.VOICE, true);
            return map;
        }

        return Maps.newHashMap();
    }
}
