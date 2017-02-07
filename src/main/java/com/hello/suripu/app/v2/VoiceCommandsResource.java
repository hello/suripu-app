package com.hello.suripu.app.v2;

import com.codahale.metrics.annotation.Timed;
import com.hello.suripu.core.models.VoiceCommandResponse;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.coredropwizard.oauth.AccessToken;
import com.hello.suripu.coredropwizard.oauth.Auth;
import com.hello.suripu.coredropwizard.oauth.ScopesAllowed;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by david on 2/3/17.
 */
@Path("/v2/voice")
public class VoiceCommandsResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(VoiceCommandsResource.class);
    private final VoiceCommandResponse voiceCommandResponse;

    public VoiceCommandsResource(final VoiceCommandResponse voiceCommandResponse) {
        this.voiceCommandResponse = checkNotNull(voiceCommandResponse, "VoiceCommandsResource voiceCommandResponse can not be null.");
    }

    @ScopesAllowed({OAuthScope.USER_BASIC})
    @GET
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/commands")
    public VoiceCommandResponse getVoiceCommands(@Auth final AccessToken token) {
        LOGGER.debug("action=get-voice-commands", token.accountId);
        return voiceCommandResponse;
    }

}
