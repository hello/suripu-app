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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            final Optional<Alert> senseAlertOptional = alertsProcessor.getSenseAlertOptional(accessToken.accountId);
            if (senseAlertOptional.isPresent()) {
                return Lists.newArrayList(senseAlertOptional.get());
            }
            final Optional<Alert> systemAlertOptional = alertsProcessor.getSystemAlertOptional(accessToken.accountId);
            if (systemAlertOptional.isPresent()) {
                return Lists.newArrayList(systemAlertOptional.get());
            }
            final Optional<Alert> pillAlertOptional = alertsProcessor.getPillAlertOptional(accessToken.accountId);
            if (pillAlertOptional.isPresent()) {
                return Lists.newArrayList(pillAlertOptional.get());
            }

            return Lists.newArrayList();
        } catch (final AlertsProcessor.UnsupportedAlertCategoryException e) {
            throw new WebApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(new JsonError(Status.INTERNAL_SERVER_ERROR.getStatusCode(), e.getMessage())).build());
        } catch (final AlertsProcessor.BadAlertRequestException e) {
            throw new WebApplicationException(Response.status(Status.BAD_REQUEST)
                    .entity(new JsonError(Status.BAD_REQUEST.getStatusCode(), e.getMessage())).build());
        }
    }



}
