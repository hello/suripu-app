package com.hello.suripu.app.v2;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.hello.suripu.core.alerts.Alert;
import com.hello.suripu.core.alerts.AlertsDAO;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.coredropwizard.oauth.AccessToken;
import com.hello.suripu.coredropwizard.oauth.Auth;
import com.hello.suripu.coredropwizard.oauth.ScopesAllowed;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/v2/alerts")
public class AlertsResource {

    private final AlertsDAO alertsDAO;

    public AlertsResource(AlertsDAO alertsDAO) {
        this.alertsDAO = alertsDAO;
    }

    @ScopesAllowed({OAuthScope.ALERTS_READ})
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Alert> get(@Auth AccessToken accessToken) {
        final Optional<Alert> alertOptional = alertsDAO.mostRecentNotSeen(accessToken.accountId);
        if(!alertOptional.isPresent()) {
            return Lists.newArrayList();
        }
        alertsDAO.seen(alertOptional.get().id());
        return Lists.newArrayList(alertOptional.get());
    }
}
