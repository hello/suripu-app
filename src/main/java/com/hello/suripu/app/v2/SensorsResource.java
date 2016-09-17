package com.hello.suripu.app.v2;

import com.codahale.metrics.annotation.Timed;
import com.hello.suripu.app.sensors.SensorResponse;
import com.hello.suripu.app.sensors.SensorViewLogic;
import com.hello.suripu.app.sensors.SensorsDataRequest;
import com.hello.suripu.app.sensors.SensorsDataResponse;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.coredropwizard.oauth.AccessToken;
import com.hello.suripu.coredropwizard.oauth.Auth;
import com.hello.suripu.coredropwizard.oauth.ScopesAllowed;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/v2/sensors")
public class SensorsResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(SensorsResource.class);

    private final SensorViewLogic viewLogic;

    public SensorsResource(final SensorViewLogic viewLogic) {
        this.viewLogic = viewLogic;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ScopesAllowed({OAuthScope.SENSORS_BASIC})
    @Timed
    public SensorResponse list(@Auth final AccessToken token) {
        LOGGER.debug("action=list-sensors account_id={}", token.accountId);
        final SensorResponse response = viewLogic.list(token.accountId, DateTime.now(DateTimeZone.UTC));
        LOGGER.debug("action=list-sensors account_id={} sensors={}", token.accountId, response.availableSensors());
        return response;
    }

    @POST
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public SensorsDataResponse data(@Auth final AccessToken token, @Valid final SensorsDataRequest request) {
<<<<<<< efd1e8cb99b0f4a1c14e0ebb2554b8dbb6b175dd
        LOGGER.debug("action=get-sensors-data account_id={}", token.accountId);
        final SensorsDataResponse response = viewLogic.data(token.accountId, request);
        LOGGER.debug("action=get-sensors-data account_id={} sensors={}", token.accountId, response.sensors().keySet());
        return response;
=======

        return viewLogic.data(token.accountId, request);
>>>>>>> Adding tests. Trying to find NPE
    }
}