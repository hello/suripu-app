package com.hello.suripu.app.resources.v1;

import com.codahale.metrics.annotation.Timed;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.core.support.SupportDAO;
import com.hello.suripu.core.support.SupportTopic;
import com.hello.suripu.coredw8.oauth.AccessToken;
import com.hello.suripu.coredw8.oauth.Auth;
import com.hello.suripu.coredw8.oauth.ScopesAllowed;


import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/v1/support")
public class SupportResource {

    private final SupportDAO supportDAO;

    public SupportResource(final SupportDAO supportDAO) {
        this.supportDAO = supportDAO;
    }

    @ScopesAllowed({OAuthScope.SUPPORT})
    @GET
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/topics")
    public List<SupportTopic> getTopics(@Auth final AccessToken accessToken) {
        return supportDAO.getTopics();
    }
}
