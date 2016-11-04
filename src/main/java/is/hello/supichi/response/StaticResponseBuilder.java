package is.hello.supichi.response;

import com.google.common.io.Resources;
import is.hello.supichi.api.Response;
import is.hello.supichi.api.Speech;
import is.hello.supichi.models.HandlerResult;
import is.hello.supichi.utils.AudioUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;

/**
 * Created by ksg on 11/3/16
 */
public class StaticResponseBuilder implements SupichiResponseBuilder {
    private final static Logger LOGGER = LoggerFactory.getLogger(StaticResponseBuilder.class);

    private final byte[] tryAgainEQ;
    private final byte[] tryAgainNonEQ;

    public StaticResponseBuilder(final byte[] tryAgainEQ, final byte[] tryAgainNonEQ) {
        this.tryAgainEQ = tryAgainEQ;
        this.tryAgainNonEQ = tryAgainNonEQ;
    }

    public static StaticResponseBuilder create() throws IOException {
        final URL eqURL = Resources.getResource("supichi/default_try_again-16k-eq.wav");
        final byte[] eqBytes = Resources.toByteArray(eqURL);

        final URL noEqURL = Resources.getResource("supichi/default_try_again-16k-noeq.wav");
        final byte[] noEqBytes = Resources.toByteArray(noEqURL);
        return new StaticResponseBuilder(eqBytes, noEqBytes);
    }

    @Override
    public byte[] response(Response.SpeechResponse.Result result, HandlerResult handlerResult, Speech.SpeechRequest request) {

        final byte[] audioBytes;
        if (request.hasEq() && request.getEq().equals(Speech.Equalizer.NONE)) {
            audioBytes = tryAgainNonEQ;
        } else {
            audioBytes = tryAgainEQ;
        }

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        LOGGER.info("response-type={} eq={}", request.getResponse().name(), request.getEq().name());

        try {
            LOGGER.debug("action=return-audio-only-response size={}", audioBytes.length);
            if (request.getResponse().equals(Speech.AudioFormat.MP3)) {
                LOGGER.debug("action=convert-pcm-to-mp3 size={}", audioBytes.length);
                final AudioFormat audioFormat = AudioUtils.DEFAULT_AUDIO_FORMAT;
                final byte[] mp3Bytes = AudioUtils.encodePcmToMp3(new AudioUtils.AudioBytes(audioBytes, audioBytes.length, audioFormat));
                outputStream.write(mp3Bytes);
            } else {
                outputStream.write(audioBytes);
            }
        } catch (IOException exception) {
            LOGGER.error("action=response-builder-error_msg={}", exception.getMessage());
        }
        return outputStream.toByteArray();
    }
}
