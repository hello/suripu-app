package is.hello.speech.models;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

/**
 * Created by ksg on 8/23/16
 */
public class UploadResponseParam {
    private final String originalValue;
    private final UploadResponseType type;

    public UploadResponseParam(String responseType) throws WebApplicationException {
        this.originalValue = responseType;

        try {
            this.type = UploadResponseType.fromString(responseType);
        } catch (IllegalArgumentException var3) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).entity("Couldn\'t parse: " + responseType + " (" + var3.getMessage() + ")").build());
        }
    }

    public String getOriginalValue() {
        return this.originalValue;
    }

    public UploadResponseType type() {
        return this.type;
    }

}
