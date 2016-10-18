package is.hello.speech.models.responsebuilder;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.S3Object;
import is.hello.speech.utils.AudioUtils;
import org.apache.commons.compress.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Created by ksg on 8/3/16
 */
public class ResponseUtils {
    private final static Logger LOGGER = LoggerFactory.getLogger(ResponseUtils.class);

    public static byte [] getAudioFromS3(final AmazonS3 s3, final String s3Bucket, final String filename) {
        byte [] audioBytes = new byte[0];
        try {
            LOGGER.debug("action=fetching-audio-from-s3 bucket={} key={}", s3Bucket, filename);

            final S3Object object = s3.getObject(s3Bucket, filename);

            final InputStream inputStream = object.getObjectContent();
            byte [] bytes = IOUtils.toByteArray(inputStream);
            audioBytes = Arrays.copyOfRange(bytes, AudioUtils.WAVE_HEADER_SIZE, bytes.length); // headerless

        } catch (IOException | AmazonS3Exception e) {
            LOGGER.error("error=fail-to-get-audio bucket={} key={} error_msg={}", s3Bucket, filename, e.getMessage());
        }

        return audioBytes;
    }
}
