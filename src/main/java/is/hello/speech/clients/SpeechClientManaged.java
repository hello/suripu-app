package is.hello.speech.clients;

import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpeechClientManaged implements Managed {

    private final static Logger LOGGER = LoggerFactory.getLogger(SpeechClientManaged.class);

    private final SpeechClient asyncSpeechClient;

    public SpeechClientManaged(SpeechClient asyncSpeechClient) {
        this.asyncSpeechClient = asyncSpeechClient;
    }

    @Override
    public void start() throws Exception {
    }

    @Override
    public void stop() throws Exception {
        LOGGER.warn("warning=shutting-down");
        asyncSpeechClient.shutdown();
        LOGGER.warn("warning=shut-down-done.");
    }
}
