package is.hello.speech.utils;

import is.hello.speech.utils.HmacSignedMessage;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HmacSignedMessageTest {

    @Test
    public void testHmac() throws DecoderException {

        final byte[] data = "hello".getBytes();
        final byte[] key = "whatever".getBytes();

        final byte[] sig = HmacSignedMessage.calculateRFC2104HMAC(data, key);
        final String hexDigest = "13960370f17799383e18da114a64c4042df19a18";

        // Derived from http://www.freeformatter.com/hmac-generator.html#ad-output
        assertEquals(hexDigest, Hex.encodeHexString(sig));

        final boolean match = HmacSignedMessage.match(data, key, Hex.decodeHex(hexDigest.toCharArray()));
        assertEquals(match, true);
    }
}
