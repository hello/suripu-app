package is.hello.supichi.response;

import com.amazonaws.services.polly.AmazonPollyAsyncClient;
import com.amazonaws.services.polly.model.SynthesizeSpeechRequest;
import com.amazonaws.services.polly.model.SynthesizeSpeechResult;
import com.amazonaws.util.IOUtils;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import is.hello.supichi.api.Response;
import is.hello.supichi.api.Speech;
import is.hello.supichi.models.GenericResponseText;
import is.hello.supichi.models.HandlerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.codahale.metrics.MetricRegistry.name;

/**
 * Created by ksg on 11/30/16
 * http://docs.aws.amazon.com/polly/latest/dg/API_SynthesizeSpeech.html
 */
public class PollyResponseBuilder implements SupichiResponseBuilder {
    private final static Logger LOGGER = LoggerFactory.getLogger(PollyResponseBuilder.class);

    private final AmazonPollyAsyncClient pollyClient;
    private final String sampleRate;
    private final String outputFormat;
    private final String voiceId;

    private final Timer timer;

    public PollyResponseBuilder(final AmazonPollyAsyncClient pollyClient, final String sampleRate, final String outputFormat, final String voiceId,
                                final MetricRegistry metricRegistry) {
        this.pollyClient = pollyClient;
        this.sampleRate = sampleRate;
        this.outputFormat = outputFormat;
        this.voiceId = voiceId;
        this.timer = metricRegistry.timer(name(PollyResponseBuilder.class, "polly-timer"));

    }

    @Override
    public byte[] response(final Response.SpeechResponse.Result result, final HandlerResult handlerResult, final Speech.SpeechRequest request) {
        final String text = (!handlerResult.responseText().isEmpty()) ? handlerResult.responseText() : GenericResponseText.UNKNOWN_TEXT;

        final SynthesizeSpeechRequest speechRequest = new SynthesizeSpeechRequest()
                .withText(text)
                .withOutputFormat(outputFormat)
                .withSampleRate(sampleRate)
                .withVoiceId(voiceId);

        final Timer.Context context = timer.time();
        final SynthesizeSpeechResult speechResult;
        try {
            speechResult = pollyClient.synthesizeSpeech(speechRequest);
        } finally {
            context.stop();
        }

        try {
            return IOUtils.toByteArray(speechResult.getAudioStream());
        } catch (IOException e) {
            LOGGER.error("action=polly-fails error_msg={}", e.getMessage());
        }
        return new byte[]{};
    }
}
