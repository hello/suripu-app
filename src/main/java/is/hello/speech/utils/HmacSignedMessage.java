package is.hello.speech.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;

public class HmacSignedMessage {

    private static final Logger LOGGER = LoggerFactory.getLogger(HmacSignedMessage.class);

    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";

    /**
     * See http://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/AuthJavaSampleHMACSignature.html
     */
    public static byte[] calculateRFC2104HMAC(final byte[] data, final byte[] key) {
        try {

            // get an hmac_sha1 key from the raw key bytes
            final SecretKeySpec signingKey = new SecretKeySpec(key, HMAC_SHA1_ALGORITHM);

            // get an hmac_sha1 Mac instance and initialize with the signing key
            final Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
            mac.init(signingKey);

            // compute the hmac on input data bytes
            return mac.doFinal(data);
        } catch (Exception e) {
            LOGGER.error("action=hmac error={}", e.getMessage());
        }
        return new byte[]{};
    }

    public static boolean match(final byte[] data, final byte[] key, final byte[] sig) {
        final byte[] computedSig = calculateRFC2104HMAC(data,key);
        return Arrays.equals(computedSig, sig);
    }
}
