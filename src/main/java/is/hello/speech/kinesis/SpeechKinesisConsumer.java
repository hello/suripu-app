package is.hello.speech.kinesis;

import com.amazonaws.services.kinesis.clientlibrary.lib.worker.KinesisClientLibConfiguration;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.Worker;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.SSEAwsKeyManagementParams;
import com.hello.suripu.core.speech.interfaces.SpeechResultIngestDAO;
import com.hello.suripu.core.speech.interfaces.SpeechTimelineIngestDAO;
import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

/**
 * Created by ksg on 8/11/16
 */
public class SpeechKinesisConsumer implements Managed {
    private final Logger LOGGER = LoggerFactory.getLogger(SpeechKinesisConsumer.class);

    private final KinesisClientLibConfiguration clientConfiguration;
    private final ExecutorService consumerExecutor;

    private final AmazonS3 s3;
    private final String s3Bucket;

    private final SSEAwsKeyManagementParams s3SSEKey;

    private final SpeechTimelineIngestDAO speechTimelineIngestDAO;
    private final SpeechResultIngestDAO speechResultIngestDAO;

    private boolean isRunning = false;

    public SpeechKinesisConsumer(final KinesisClientLibConfiguration clientConfiguration,
                                 final ExecutorService consumerExecutor,
                                 final AmazonS3 s3,
                                 final String s3Bucket,
                                 final SSEAwsKeyManagementParams s3SSEKey,
                                 final SpeechTimelineIngestDAO speechTimelineIngestDAO,
                                 final SpeechResultIngestDAO speechResultIngestDAO) {
        this.clientConfiguration = clientConfiguration;
        this.consumerExecutor = consumerExecutor;
        this.s3 = s3;
        this.s3SSEKey = s3SSEKey;
        this.s3Bucket = s3Bucket;
        this.speechTimelineIngestDAO = speechTimelineIngestDAO;
        this.speechResultIngestDAO = speechResultIngestDAO;
    }

    @Override
    public void start() throws Exception {
        LOGGER.debug("action=start-kinesis-consumer running={}", isRunning);
        consumerExecutor.execute(() -> {
            try {
                isRunning = true;
                consumeKinesisStream();
            } catch (Exception e) {
                isRunning = false;
                LOGGER.error("error=fail-to-start-kinesis-consumer error_msg={}", e.getMessage());
                try {
                    Thread.sleep(5000L);
                } catch (InterruptedException interrupted) {
                    LOGGER.warn("warning=kinesis-consumer-exit-interrupted-sleep");
                }
                System.exit(1);
            }});
    }

    @Override
    public void stop() throws Exception {
        LOGGER.debug("action=stop-kinesis-consumer");
        isRunning = false;
    }

    private void consumeKinesisStream() {
        final SpeechKinesisProcessorFactory factory = new SpeechKinesisProcessorFactory(s3Bucket, s3, s3SSEKey, speechTimelineIngestDAO, speechResultIngestDAO);
        LOGGER.info("info=kinesis-initial-position value={}", this.clientConfiguration.getInitialPositionInStream());
        final Worker worker = new Worker(factory, this.clientConfiguration);
        worker.run();
    }
}
