package is.hello.supichi.resources.ping;

import com.hello.suripu.core.util.HelloHttpHeader;
import com.librato.rollout.RolloutClient;
import is.hello.supichi.handler.AudioRequestHandler;
import is.hello.supichi.utils.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

@Path("/v2/ping")
public class PingResource {
    @Inject
    RolloutClient feature;


    private static final Logger LOGGER = LoggerFactory.getLogger(PingResource.class);
    private final AudioRequestHandler audioRequestHandler;


    public PingResource(final AudioRequestHandler audioRequestHandler) {
        this.audioRequestHandler = audioRequestHandler;
    }

    @Context
    HttpServletRequest request;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String ping() {
        final String ipAddress = Metadata.getIpAddress(request);
        final String senseId = this.request.getHeader(HelloHttpHeader.SENSE_ID) != null ? this.request.getHeader(HelloHttpHeader.SENSE_ID) : "UNK";
        LOGGER.debug("action=ping sense_id={} ip_address={}", senseId, ipAddress);

        return "PONG";
    }
}
