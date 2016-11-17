package is.hello.supichi.clients;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import is.hello.supichi.configuration.AudioConfiguration;
import is.hello.supichi.models.SpeechServiceResult;

import java.io.IOException;

/**
 * Created by ksg on 10/21/16
 */
public class InstrumentedSpeechClient extends SpeechClient {

    private final Timer streamer;

    public InstrumentedSpeechClient(AudioConfiguration configuration, final MetricRegistry metricRegistry) throws IOException {
        super(configuration);
        this.streamer = metricRegistry.timer(MetricRegistry.name(InstrumentedSpeechClient.class, "transcript-timer"));
    }

    @Override
    public SpeechServiceResult stream(final String senseId, final byte [] bytes, int samplingRate) throws InterruptedException, IOException {
        final Timer.Context context = streamer.time();
        try {
            return super.stream(senseId, bytes, samplingRate);
        } finally {
            context.stop();
        }
    }
}
