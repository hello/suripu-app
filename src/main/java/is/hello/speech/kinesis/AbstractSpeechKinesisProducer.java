
package is.hello.speech.kinesis;


import com.amazonaws.services.kinesis.producer.KinesisProducer;
import io.dropwizard.lifecycle.Managed;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by ksg on 8/9/16
 */
public abstract class AbstractSpeechKinesisProducer implements Managed {

    private final static Logger LOGGER = LoggerFactory.getLogger(AbstractSpeechKinesisProducer.class);

    private final static int METRICS_SCHEDULE_MINUTES = 1;

    private final String streamName;
    protected final BlockingQueue<KinesisData> inputQueue;
    protected final KinesisProducer kinesisProducer;

    protected final ExecutorService executor;
    protected final ScheduledExecutorService metricsScheduledExecutor;

    protected volatile boolean isRunning = false;
    protected final AtomicLong recordsPut = new AtomicLong(0);

    protected AbstractSpeechKinesisProducer(final String streamName,
                                            final BlockingQueue<KinesisData> inputQueue,
                                            final ExecutorService executor,
                                            final ScheduledExecutorService metricsScheduledExecutor,
                                            final KinesisProducer kinesisProducer) {
        this.streamName = streamName;
        this.inputQueue = inputQueue;
        this.executor = executor;
        this.metricsScheduledExecutor = metricsScheduledExecutor;
        this.kinesisProducer = kinesisProducer;
    }


    @Override
    public void start() throws Exception {
        final DateTime startTime = DateTime.now(DateTimeZone.UTC);
        LOGGER.debug("action=start-speech-kinesis-producer stream={} is_running={}", streamName, isRunning);
        executor.execute(() -> {
                    try {
                        isRunning = true;
                        putData();
                    } catch (Exception e) {
                        isRunning = false;
                        LOGGER.warn("error=kinesis-producer-not-running stream={} error_msg={}", streamName, e.getMessage());
                        try {
                            Thread.sleep(5000L);
                        } catch (InterruptedException e1) {
                            LOGGER.warn("warning=speech-kinesis-producer-sleep-interrupted");
                        }
                        System.exit(1);
                    }
                }
        );

        // report metrics every minute
        metricsScheduledExecutor.scheduleAtFixedRate(() -> {
            final long elapsedSeconds = new Interval(DateTime.now(DateTimeZone.UTC), startTime).toDuration().getMillis() / 1000L;
            LOGGER.info("metric=records-put total={} elapsed={}", recordsPut(), elapsedSeconds);
        }, METRICS_SCHEDULE_MINUTES, METRICS_SCHEDULE_MINUTES, TimeUnit.MINUTES);
    }

    protected abstract void putData() throws Exception;

    @Override
    public  void stop() {
        isRunning = false;
        LOGGER.info("action=kinesis-producer-stopped stream={}", streamName);
    }

    public long recordsPut() {
        return recordsPut.get();
    }

}
