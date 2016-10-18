package is.hello.speech.response;

import com.google.common.base.Optional;
import com.ibm.watson.developer_cloud.text_to_speech.v1.TextToSpeech;
import com.ibm.watson.developer_cloud.text_to_speech.v1.model.Voice;
import is.hello.speech.api.Response;
import is.hello.speech.api.Speech;
import is.hello.speech.models.HandlerResult;
import is.hello.speech.models.responsebuilder.DefaultResponseBuilder;
import is.hello.speech.utils.AudioUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by ksg on 8/3/16
 */
public class WatsonResponseBuilder implements SupichiResponseBuilder {
    private final static Logger LOGGER = LoggerFactory.getLogger(WatsonResponseBuilder.class);

    private final TextToSpeech watson;
    private final Voice watsonVoice;

    public WatsonResponseBuilder(final TextToSpeech watson, final String voice) {
        this.watson = watson;
        this.watsonVoice = this.watson.getVoice(voice).execute();

    }

    @Override
    public byte[] response(final Response.SpeechResponse.Result result,
                           final HandlerResult handlerResult,
                           final Speech.SpeechRequest request) {



        final String text = (!handlerResult.responseText().isEmpty()) ? handlerResult.responseText() :
                DefaultResponseBuilder.DEFAULT_TEXT.get(Response.SpeechResponse.Result.UNKNOWN);

        try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             final InputStream watsonStream = watson.synthesize(text, watsonVoice, AudioUtils.WATSON_AUDIO_FORMAT).execute()) {

            final AudioUtils.AudioBytes watsonAudio = AudioUtils.convertStreamToBytesWithWavHeader(watsonStream);

            AudioUtils.AudioBytes equalizedBytes = watsonAudio;
            // equalized audio first, 16K not supported
            if(request.hasEq() && request.getEq().equals(Speech.Equalizer.SENSE_ONE)) {
                equalizedBytes = AudioUtils.equalize(watsonAudio.bytes, Optional.absent());
            }

            // down-sample audio from 22050 to 16k, upload converted bytes to S3
            final AudioUtils.AudioBytes downSampledBytes = AudioUtils.downSampleAudio(
                    equalizedBytes.bytes,
                    equalizedBytes.format,
                    AudioUtils.SENSE_SAMPLING_RATE
            );

            LOGGER.info("response-type={} eq={}", request.getResponse().name(), request.getEq().name());
            if (request.getResponse().equals(Speech.AudioFormat.MP3)) {
                LOGGER.debug("action=convert-pcm-to-mp3 size={}", downSampledBytes.contentSize);
                final byte[] mp3Bytes = AudioUtils.encodePcmToMp3(downSampledBytes);
                outputStream.write(mp3Bytes);
            } else {
                LOGGER.debug("action=return-PCM-audio size={}", downSampledBytes.contentSize);
                outputStream.write(downSampledBytes.bytes);
            }
            return outputStream.toByteArray();
        } catch (IOException e) {
            LOGGER.error("action=watson-down-sample-fails error_msg={}", e.getMessage());
        }

        return new byte[]{};
    }
}
