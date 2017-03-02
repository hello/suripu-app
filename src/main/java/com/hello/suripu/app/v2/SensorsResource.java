package com.hello.suripu.app.v2;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Optional;
import com.hello.suripu.app.sensors.BatchQuery;
import com.hello.suripu.app.sensors.BatchQueryResponse;
import com.hello.suripu.app.sensors.SensorResponse;
import com.hello.suripu.app.sensors.SensorViewLogic;
import com.hello.suripu.core.actions.Action;
import com.hello.suripu.core.actions.ActionProcessor;
import com.hello.suripu.core.actions.ActionType;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.coredropwizard.oauth.AccessToken;
import com.hello.suripu.coredropwizard.oauth.Auth;
import com.hello.suripu.coredropwizard.oauth.ScopesAllowed;
import com.hello.suripu.coredropwizard.resources.BaseResource;
import com.librato.rollout.RolloutClient;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/v2/sensors")
public class SensorsResource extends BaseResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(SensorsResource.class);

    private final SensorViewLogic viewLogic;

    @Inject
    RolloutClient flipper;

    @Inject
    ActionProcessor actionProcessor;

    public SensorsResource(final SensorViewLogic viewLogic) {
        this.viewLogic = viewLogic;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ScopesAllowed({OAuthScope.SENSORS_BASIC})
    @Timed
    public SensorResponse list(@Auth final AccessToken token) {

        LOGGER.debug("action=list-sensors account_id={}", token.accountId);
        final DateTime now = DateTime.now(DateTimeZone.UTC);
        final SensorResponse response = viewLogic.list(token.accountId, now);
        LOGGER.debug("action=list-sensors account_id={} sensors={}", token.accountId, response.availableSensors());

        this.actionProcessor.add(new Action(token.accountId, ActionType.ROOM_CONDITIONS_CURRENT, Optional.of(response.status().toString()), now, Optional.absent()));

        return response;
    }

    @POST
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public BatchQueryResponse data(@Auth final AccessToken token, @Valid final BatchQuery query) {
        LOGGER.debug("action=get-sensors-data account_id={}", token.accountId);
        final BatchQueryResponse response = viewLogic.data(token.accountId, query);
        LOGGER.debug("action=get-sensors-data account_id={} sensors={}", token.accountId, response.sensors().keySet());
        this.actionProcessor.add(new Action(token.accountId, ActionType.ROOM_CONDITIONS_CURRENT, Optional.absent(), DateTime.now(DateTimeZone.UTC), Optional.absent()));

        return response;
    }
}