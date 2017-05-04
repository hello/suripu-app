package com.hello.suripu.app.v2;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.hello.suripu.app.alerts.AlertsProcessor;
import com.hello.suripu.core.alerts.Alert;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.core.util.JsonError;
import com.hello.suripu.coredropwizard.oauth.AccessToken;
import com.hello.suripu.coredropwizard.oauth.Auth;
import com.hello.suripu.coredropwizard.oauth.ScopesAllowed;
import com.librato.rollout.RolloutClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.List;

@Path("/v2/alerts")
public class AlertsResource {

    @Inject
    RolloutClient flipper;

    private static final Logger LOGGER = LoggerFactory.getLogger(AlertsResource.class);

    private final AlertsProcessor alertsProcessor;

    public AlertsResource(final AlertsProcessor alertsProcessor) {
        this.alertsProcessor = alertsProcessor;
    }

    @ScopesAllowed({OAuthScope.ALERTS_READ})
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Alert> get(@Auth AccessToken accessToken) {
        try {
            final Optional<Alert> existingAlertOptional = alertsProcessor.getExistingAlertOptional(accessToken.accountId);
            if (existingAlertOptional.isPresent()) {
                return Lists.newArrayList(existingAlertOptional.get());
            }
            return Lists.newArrayList();
        } catch (final AlertsProcessor.UnsupportedAlertCategoryException e) {
            throw new WebApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(new JsonError(Status.INTERNAL_SERVER_ERROR.getStatusCode(), e.getMessage())).build());
        }
    }



}
