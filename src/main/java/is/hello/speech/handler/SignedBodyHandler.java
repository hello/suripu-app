package is.hello.speech.handler;

import com.google.common.base.Optional;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hello.suripu.core.db.KeyStore;
import is.hello.speech.api.Speech;
import is.hello.speech.utils.HmacSignedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class SignedBodyHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SignedBodyHandler.class);

    private static final int PREFIX_LENGTH = 4; // bytes in network byte order
    private static final int SIGNATURE_LENGTH = 20; // HMAC size
    private static final int MAX_PB_SIZE = 40;

    private final KeyStore keystore;

    public SignedBodyHandler(final KeyStore keystore) {
        this.keystore = keystore;
    }

    public UploadData extractUploadData(final String senseId, byte[] signedBody) throws InvalidSignedBodyException, InvalidSignatureException {
        // Check HMAC signature
        final byte[] body = extractSignature(senseId, signedBody);

        // extract protobuf and audio
        return extractData(senseId, body);
    }

    protected byte[] extractSignature(final String senseId, byte[] signedBody) throws InvalidSignedBodyException, InvalidSignatureException {
        final Optional<byte[]> optionalKey = keystore.getStrict(senseId);
        if(!optionalKey.isPresent()) {
            LOGGER.error("error=no-keys-found sense_id={}", senseId);
            throw new InvalidSignedBodyException("invalid-key");
        }

        if(signedBody.length <= SIGNATURE_LENGTH) {
            LOGGER.error("error=invalid-body-length expects_at_least={} actual={}", SIGNATURE_LENGTH, signedBody.length);
            throw new InvalidSignedBodyException("invalid-body-length");
        }

        final byte[] sig = Arrays.copyOfRange(signedBody, signedBody.length - SIGNATURE_LENGTH, signedBody.length);
        final byte[] body = Arrays.copyOfRange(signedBody, 0, signedBody.length - SIGNATURE_LENGTH);

        if(!HmacSignedMessage.match(body, optionalKey.get(), sig)) {
            LOGGER.error("error=HMAC-mismatch");
            throw new InvalidSignatureException("HMAC-mismatch");
        }
        return body;
    }

    protected UploadData extractData(final String senseId, byte[] body) throws InvalidSignedBodyException {

        // parse body for PB and audio
        final int bodySize = body.length;
        int totalRead = 0;

        if (bodySize < PREFIX_LENGTH) {
            throw new InvalidSignedBodyException("insufficient-bytes-prefix-length");
        }

        final ByteArrayInputStream inputStream = new ByteArrayInputStream(body);

        // read prefix
        final byte[] pbSizeBytes = new byte[PREFIX_LENGTH];
        int readSize = inputStream.read(pbSizeBytes, 0, PREFIX_LENGTH);
        if (readSize != PREFIX_LENGTH) {
            LOGGER.error("error=upload-prefix-read-fail expect={} read={}", PREFIX_LENGTH, readSize);
            throw new InvalidSignedBodyException("read-inputstream-error-for-protobuf-size");
        }

        final int  pbSize = ByteBuffer.wrap(pbSizeBytes).getInt();

        // read protobuf
        totalRead += readSize;
        if ((totalRead + pbSize) > bodySize) {
            throw new InvalidSignedBodyException("insufficient-bytes-protobuf");
        }

        LOGGER.debug("action=get-pb-size size={}", pbSize);
        if (pbSize <= 0 || pbSize > MAX_PB_SIZE) {
            throw new InvalidSignedBodyException("invalid-pb-size");
        }

        final byte[] pbBytes = new byte[pbSize];
        readSize = inputStream.read(pbBytes, 0, pbSize);
        if (readSize != pbSize) {
            LOGGER.error("error=upload-protobuf-size-read-fail expect={} read={} sense_id={}",
                    pbSize, readSize, senseId);
            throw new InvalidSignedBodyException("read-inputstream-error-for-protobuf-data");
        }

        // read audio data
        final int audioSize = body.length - SIGNATURE_LENGTH - PREFIX_LENGTH - pbSize;
        final byte[] audioBody = new byte[audioSize];
        readSize = inputStream.read(audioBody, 0, audioSize);
        if (readSize != audioSize) {
            LOGGER.error("error=upload-protobuf-bytes-read-fail expect={} read={} sense_id={}", pbSize, readSize, senseId);
            throw new InvalidSignedBodyException("read-inputstream-error-for-audio-data");
        }

        try {
            final Speech.SpeechRequest request = Speech.SpeechRequest.parseFrom(pbBytes);
            return new UploadData(pbSize, request, audioBody);
        } catch (InvalidProtocolBufferException e) {
            LOGGER.error("error=fail-to-decode-upload-speech-data error_msg={} sense_id={}", e.getMessage(), senseId);
        }

        throw new InvalidSignedBodyException("fail-to-parse-upload-data");
    }
}
