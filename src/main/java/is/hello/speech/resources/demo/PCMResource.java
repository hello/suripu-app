package is.hello.speech.resources.demo;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.util.IOUtils;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import java.io.IOException;

@Path("/pcm")
public class PCMResource {

    private final AmazonS3 s3;
    private final String bucketName;

    @Context
    HttpServletRequest request;

    public PCMResource(final AmazonS3 s3, final String bucketName) {
        this.s3 = s3;
        this.bucketName = bucketName;
    }

    @POST
    public byte[] pcm() throws IOException {
        final S3Object object = s3.getObject(bucketName, "voice/watson-16k.wav");
        final byte[] byteArray = IOUtils.toByteArray(object.getObjectContent());
        return byteArray;
    }
}
