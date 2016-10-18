package is.hello.speech.resources.demo;

import com.codahale.metrics.annotation.Timed;
import com.hello.suripu.core.util.HelloHttpHeader;
import is.hello.speech.handler.AudioRequestHandler;
import is.hello.speech.handler.RawRequest;
import is.hello.speech.handler.WrappedResponse;
import is.hello.speech.utils.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;


@Path("/demo/upload")
@Produces(MediaType.APPLICATION_JSON)
public class DemoUploadResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoUploadResource.class);
    private final AudioRequestHandler audioRequestHandler;

    @Context
    HttpServletRequest request;

    public DemoUploadResource(final AudioRequestHandler audioRequestHandler) {
        this.audioRequestHandler = audioRequestHandler;
    }

    @Path("/audio")
    @POST
    @Timed
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public byte[] streaming(final byte[] signedBody) {
        final String senseId = this.request.getHeader(HelloHttpHeader.SENSE_ID);
        if(senseId == null) {
            LOGGER.error("error=missing-sense-id-header");
            throw new WebApplicationException(Response.Status.BAD_REQUEST);
        }

        final RawRequest rawRequest = RawRequest.create(signedBody, senseId, Metadata.getIpAddress(request));
        final WrappedResponse response = audioRequestHandler.handle(rawRequest);
        if(response.hasError()) {
            throw new WebApplicationException(Response.Status.BAD_REQUEST);
        }
        return response.content();
    }
}
