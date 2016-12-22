package is.hello.supichi.response;

import com.amazonaws.services.s3.AmazonS3;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.base.Optional;
import com.ibm.watson.developer_cloud.text_to_speech.v1.TextToSpeech;
import com.ibm.watson.developer_cloud.text_to_speech.v1.model.Voice;
import is.hello.supichi.api.Response;
import is.hello.supichi.api.Speech;
import is.hello.supichi.models.GenericResponseText;
import is.hello.supichi.models.HandlerResult;
import is.hello.supichi.utils.AudioUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static com.codahale.metrics.MetricRegistry.name;

/**
 * Created by ksg on 8/3/16
 */
public class WatsonResponseBuilder implements SupichiResponseBuilder {
    private final static Logger LOGGER = LoggerFactory.getLogger(WatsonResponseBuilder.class);

    private final TextToSpeech watson;
    private final Voice watsonVoice;
    private AmazonS3 amazonS3;
    private final Timer timer;
    private final String s3BucketNameForMp3;

    public WatsonResponseBuilder(final TextToSpeech watson, final String voice, final AmazonS3 amazonS3, final MetricRegistry metricRegistry, final String s3BucketNameForMp3) {
        this.watson = watson;
        this.watsonVoice = this.watson.getVoice(voice).execute();
        this.timer = metricRegistry.timer(name(WatsonResponseBuilder.class, "watson-timer"));
        this.amazonS3 = amazonS3;
        this.s3BucketNameForMp3 = s3BucketNameForMp3;
    }

    @Override
    public byte[] response(final Response.SpeechResponse.Result result,
                           final HandlerResult handlerResult,
                           final Speech.SpeechRequest request) {


        if(handlerResult.optionalResult.isPresent() && handlerResult.optionalResult.get().url.isPresent()) {
            final String url = handlerResult.optionalResult.get().url.get();
            try (final InputStream in = amazonS3.getObject(s3BucketNameForMp3, url).getObjectContent()){
                LOGGER.info("mp3_url={}", url);
                return IOUtils.toByteArray(in);
            } catch (IOException e) {
                LOGGER.error("action=get-mp3-from-s3 key={} error={}", url, e.getMessage());
                return new byte[]{0,0,0};
            }
        }

        final String text = (!handlerResult.responseText().isEmpty()) ? handlerResult.responseText() : GenericResponseText.UNKNOWN_TEXT;

        try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            final Timer.Context context = timer.time();
            final InputStream watsonStream;
            try {
                watsonStream = watson.synthesize(text, watsonVoice, AudioUtils.WATSON_AUDIO_FORMAT).execute();
            } finally {
                context.stop();
            }

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

        return new byte[]{0,0,0};
    }
}
