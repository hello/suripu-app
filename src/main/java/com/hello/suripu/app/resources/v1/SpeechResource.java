package com.hello.suripu.app.resources.v1;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.core.speech.SpeechResult;
import com.hello.suripu.core.speech.SpeechResultDynamoDBDAO;
import com.hello.suripu.coredw8.oauth.AccessToken;
import com.hello.suripu.coredw8.oauth.Auth;
import com.hello.suripu.coredw8.oauth.ScopesAllowed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;

@Path("/v1/speech")
public class SpeechResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpeechResource.class);

    private final SpeechResultDynamoDBDAO speechResultDynamoDBDAO;
    private final DeviceDAO deviceDAO;


    public SpeechResource(final SpeechResultDynamoDBDAO speechResultDynamoDBDAO, final DeviceDAO deviceDAO) {
        this.speechResultDynamoDBDAO = speechResultDynamoDBDAO;
        this.deviceDAO = deviceDAO;
    }

    // TODO: switch oauth-scope to SPEECH_COMMAND later
    @ScopesAllowed({OAuthScope.SENSORS_BASIC})
    @Timed
    @GET
    @Path("onboarding")
    @Produces(MediaType.APPLICATION_JSON)
    public List<SpeechResult> getOnboardingResults(@Auth final AccessToken accessToken,
                                                   @DefaultValue("3") @QueryParam("look_back") final int lookBackMinutes) {

        final Long accountId = accessToken.accountId;
        final Optional<DeviceAccountPair> deviceIdPair = deviceDAO.getMostRecentSensePairByAccountId(accountId);
        if (!deviceIdPair.isPresent()) {
            LOGGER.warn("account-id={} device-id-pair=not-found", accountId);
            throw new WebApplicationException(Response.Status.BAD_REQUEST);
        }

        final String senseId = deviceIdPair.get().externalDeviceId;
        final Optional<SpeechResult> result = speechResultDynamoDBDAO.getLatest(accountId, senseId, lookBackMinutes);

        if (result.isPresent()) {
            LOGGER.debug("action=get-latest-speech-result sense_id={} found=true command={}", senseId, result.get().command);
            return Lists.newArrayList(result.get());
        }

        LOGGER.debug("action=no-recent-speech-commands lookback={} sense_id={} account_id={}", lookBackMinutes, senseId, accountId);
        return Collections.emptyList();
    }
}