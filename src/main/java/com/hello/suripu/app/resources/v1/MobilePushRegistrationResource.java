package com.hello.suripu.app.resources.v1;

import com.codahale.metrics.annotation.Timed;
import com.hello.suripu.core.db.AccountDAO;
import com.hello.suripu.core.models.MobilePushRegistration;
import com.hello.suripu.core.notifications.NotificationSubscriptionDAOWrapper;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.coredropwizard.oauth.AccessToken;
import com.hello.suripu.coredropwizard.oauth.Auth;
import com.hello.suripu.coredropwizard.oauth.ScopesAllowed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;

@Path("/v1/notifications")
public class MobilePushRegistrationResource {


    private static final Logger LOGGER = LoggerFactory.getLogger(MobilePushRegistrationResource.class);
    private final NotificationSubscriptionDAOWrapper notificationSubscriptionDAOWrapper;
    private final AccountDAO accountDAO;

    public MobilePushRegistrationResource(
            final NotificationSubscriptionDAOWrapper notificationSubscriptionDAOWrapper,
            final AccountDAO accountDAO) {
        this.notificationSubscriptionDAOWrapper = notificationSubscriptionDAOWrapper;
        this.accountDAO = accountDAO;
    }

    @ScopesAllowed({OAuthScope.PUSH_NOTIFICATIONS})
    @Timed
    @POST
    @Path("/registration")
    @Consumes(MediaType.APPLICATION_JSON)
    public void registerDevice(@Auth final AccessToken accessToken,
                               final @Valid MobilePushRegistration mobilePushRegistration) {

        LOGGER.debug("Receive push notification registration for account_id {}. {}", accessToken.accountId, mobilePushRegistration);
        final MobilePushRegistration mobilePushRegistrationWithOauthToken = MobilePushRegistration.withOauthToken(mobilePushRegistration, accessToken.serializeAccessToken());
        notificationSubscriptionDAOWrapper.subscribe(accessToken.accountId, mobilePushRegistrationWithOauthToken);
    }

    @ScopesAllowed({OAuthScope.PUSH_NOTIFICATIONS})
    @DELETE
    @Timed
    @Path("/registration")
    @Consumes(MediaType.APPLICATION_JSON)
    public void delete(
            @Auth final AccessToken accessToken,
            @Valid MobilePushRegistration mobilePushRegistration) {

        boolean deleted = notificationSubscriptionDAOWrapper.unsubscribe(accessToken.accountId, mobilePushRegistration.deviceToken);
        if(!deleted) {
            LOGGER.warn("{} Was not successfully deleted for account = {}", mobilePushRegistration.deviceToken, accessToken.accountId);
        }
    }
}
