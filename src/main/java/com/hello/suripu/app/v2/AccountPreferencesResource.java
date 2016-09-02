package com.hello.suripu.app.v2;

import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.core.preferences.AccountPreferencesDAO;
import com.hello.suripu.core.preferences.PreferenceName;
import com.hello.suripu.coredropwizard.oauth.AccessToken;
import com.hello.suripu.coredropwizard.oauth.Auth;
import com.hello.suripu.coredropwizard.oauth.ScopesAllowed;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Map;

@Path("/v2/account/preferences")
public class AccountPreferencesResource {
    private final AccountPreferencesDAO preferencesDAO;

    public AccountPreferencesResource(final AccountPreferencesDAO preferencesDAO) {
        this.preferencesDAO = preferencesDAO;
    }

    @ScopesAllowed({OAuthScope.PREFERENCES})
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<PreferenceName, Boolean> get(@Auth final AccessToken accessToken) {
        return preferencesDAO.get(accessToken.accountId);
    }

    @ScopesAllowed({OAuthScope.PREFERENCES})
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Map<PreferenceName, Boolean> put(
            @Auth final AccessToken accessToken,
            @Valid final Map<PreferenceName, Boolean> accountPreference) {
        return preferencesDAO.putAll(accessToken.accountId, accountPreference);
    }
}
