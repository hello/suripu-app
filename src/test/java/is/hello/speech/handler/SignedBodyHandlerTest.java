package is.hello.speech.handler;

import com.google.common.base.Optional;
import com.hello.suripu.core.db.KeyStore;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SignedBodyHandlerTest {

    final KeyStore keystore = mock(KeyStore.class);

    @Test(expected = InvalidSignedBodyException.class)
    public void testUploadSignedAudioNoKey() throws IOException, InterruptedException, InvalidSignedBodyException, InvalidSignatureException {
        when(keystore.getStrict(any(String.class))).thenReturn(Optional.absent());

        final SignedBodyHandler signedBodyHandler = new SignedBodyHandler(keystore);
        try {
            signedBodyHandler.extractSignature("sense", new byte[]{});
        } catch (InvalidSignedBodyException w) {
            assertEquals(w.getMessage(), "invalid-key");
            throw w;
        }
    }

    @Test(expected = InvalidSignedBodyException.class)
    public void testUploadSignedAudioInvalid() throws IOException, InterruptedException, InvalidSignedBodyException, InvalidSignatureException {
        when(keystore.getStrict(any(String.class))).thenReturn(Optional.of(new byte[]{}));

        final SignedBodyHandler signedBodyHandler = new SignedBodyHandler(keystore);

        try {
            signedBodyHandler.extractSignature("sense", new byte[]{});
        } catch (InvalidSignedBodyException w) {
            assertEquals(w.getMessage(), "invalid-body-length");
            throw w;
        }
    }


    @Test (expected = InvalidSignatureException.class)
    public void testUploadSignedAudioContentTooShort() throws IOException, InterruptedException, InvalidSignedBodyException, InvalidSignatureException {
        when(keystore.getStrict(any(String.class))).thenReturn(Optional.of(new byte[]{}));

        final SignedBodyHandler signedBodyHandler = new SignedBodyHandler(keystore);

        try {
            final byte[] body = new byte[21];
            Arrays.fill(body, (byte) 1);
            signedBodyHandler.extractSignature("sense", body);
        } catch (InvalidSignatureException w) {
            assertEquals(w.getMessage(), "HMAC-mismatch");
            throw w;
        }
    }

    @Test
    public void testInvalidPrefixLength() {
        when(keystore.getStrict(any(String.class))).thenReturn(Optional.of(new byte[]{}));

        final SignedBodyHandler signedBodyHandler = new SignedBodyHandler(keystore);

        try {
            signedBodyHandler.extractData("sense8", new byte[]{1,1,1});
        } catch (InvalidSignedBodyException e) {
            assertEquals(e.getMessage(), "insufficient-bytes-prefix-length");
        }
    }

    @Test
    public void testInvalidProtobufSize() {
        when(keystore.getStrict(any(String.class))).thenReturn(Optional.of(new byte[]{}));

        final SignedBodyHandler signedBodyHandler = new SignedBodyHandler(keystore);

        try {
            signedBodyHandler.extractData("sense8", new byte[]{0,0,0,8,1,2,3});
        } catch (InvalidSignedBodyException e) {
            assertEquals(e.getMessage(), "insufficient-bytes-protobuf");
        }
    }

}
