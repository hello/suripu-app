package is.hello.supichi.kinesis;

import com.amazonaws.services.kinesis.producer.Attempt;
import com.amazonaws.services.kinesis.producer.KinesisProducer;
import com.amazonaws.services.kinesis.producer.Metric;
import com.amazonaws.services.kinesis.producer.UserRecordFailedException;
import com.amazonaws.services.kinesis.producer.UserRecordResult;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;
import com.hello.suripu.core.speech.models.SpeechResult;
import com.hello.suripu.core.speech.models.WakeWord;
import is.hello.supichi.api.SpeechResultsKinesis;
import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by ksg on 8/9/16
 */
public class SpeechKinesisProducer extends AbstractSpeechKinesisProducer {

    private final static Logger LOGGER = LoggerFactory.getLogger(SpeechKinesisProducer.class);

    private final static Long KINESIS_MAX_RECORDS_IN_QUEUE = 10000L;

    private final String streamName;
    private final Boolean logUUID;

    public SpeechKinesisProducer(final String streamName,
                                 final BlockingQueue<KinesisData> inputQueue,
                                 final KinesisProducer kinesisProducer,
                                 final ExecutorService executor,
                                 final ScheduledExecutorService metricsScheduledExecutor,
                                 final Boolean logUUID) {
        super(streamName, inputQueue, executor, metricsScheduledExecutor, kinesisProducer);
        this.streamName = streamName;
        this.logUUID = logUUID;
    }

    public Boolean addResult(final SpeechResult result, final SpeechResultsKinesis.SpeechResultsData.Action action, final byte[] audioBytes) {
        try {
            inputQueue.offer(new KinesisData(result, action, audioBytes), 1000L, TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException e) {
            LOGGER.warn("error=fail-to-put-audio-data-in-queue error_msg={}", e.getMessage());
            return false;
        }
    }

    @Override
    protected void putData() throws Exception {
        LOGGER.debug("action=KPL-running");
        do {
            if (!inputQueue.isEmpty()) {
                LOGGER.debug("action=kpl-put-data queue_size={}", inputQueue.size());
                final SpeechResultsKinesis.SpeechResultsData speechResult = getSpeechResultsData(inputQueue.take());

                final String partitionKey = speechResult.getSenseId();
                final ByteBuffer payload = ByteBuffer.wrap(speechResult.toByteArray());

                while (kinesisProducer.getOutstandingRecordsCount() > KINESIS_MAX_RECORDS_IN_QUEUE) {
                    LOGGER.warn("warning=too-many-outstanding-records-in-kp records_size={} action=sleep-1", kinesisProducer.getOutstandingRecordsCount());
                    Thread.sleep(1000L);
                }
                recordsPut.getAndIncrement();
                final ListenableFuture<UserRecordResult> putFutures = kinesisProducer.addUserRecord(streamName, partitionKey, payload);

                Futures.addCallback(putFutures, new FutureCallback<UserRecordResult>() {
                    @Override
                    public void onSuccess(@Nullable final UserRecordResult putResult) {
                        if (putResult != null) {
                            LOGGER.debug("action=put-record-success partition_key={} shard={} seq_num={} attempts={} time_taken={}",
                                    partitionKey, putResult.getShardId(), putResult.getSequenceNumber(),
                                    putResult.getAttempts().size(), getTotalTime(putResult));
                        }
                    }

                    @Override
                    public void onFailure(@NotNull Throwable throwable) {
                        if (throwable instanceof UserRecordFailedException) {
                            final UserRecordFailedException e = (UserRecordFailedException) throwable;
                            final UserRecordResult putResult = e.getResult();
                            final int numAttempts = putResult.getAttempts().size();
                            final Attempt lastAttempt = putResult.getAttempts().get(numAttempts-1);

                            LOGGER.error("error=put-record-fail partition_key={} shard={} attempts={} time_taken={} last_error_code={} last_error_msg={}",
                                    partitionKey, putResult.getShardId(), numAttempts, getTotalTime(putResult),
                                    lastAttempt.getErrorCode(), lastAttempt.getErrorMessage());
                        }
                    }
                });
            } else {
                // nothing in queue, sleep a little
                if (isRunning) {
                    Thread.sleep(500L);
                }
            }
        } while (isRunning);
    }

    @Override
    public long recordsPut() {
        // UserRecordsPut, AllErrors, RequestTime
        try {
            return kinesisProducer.getMetrics("UserRecordsPut").stream()
                    .filter(m -> m.getDimensions().size() == 2)
                    .findFirst()
                    .map(Metric::getSum)
                    .orElse(0.0)
                    .longValue();
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.error("error=kinesis-get-metrics-fail error_mss={}", e.getMessage());
        }
        return super.recordsPut() - kinesisProducer.getOutstandingRecordsCount();
    }

    @Override
    public void stop() {
        isRunning = false;
        try {
            LOGGER.debug("action=stop-kinesis-producer-sleep time=1-second why=wait-for-other-sleep-to-complete");
            Thread.sleep(2000L);
        } catch (InterruptedException e) {
            LOGGER.warn("warning=stop-sleep-interrupted!");
        }

        LOGGER.debug("action=before-flush-sync");
        kinesisProducer.flushSync();

        LOGGER.debug("action=before-destroy");
        kinesisProducer.destroy();
        super.stop();
    }

    private long getTotalTime(final UserRecordResult putResult) {
        return putResult.getAttempts().stream()
                .mapToLong(a -> a.getDelay() + a.getDuration())
                .sum();
    }

    private SpeechResultsKinesis.SpeechResultsData getSpeechResultsData(final KinesisData data) {
        final String uuid = (logUUID) ? data.speechResult.audioIdentifier : "UUID";
        LOGGER.debug("action=adding-to-kpl sense_id={} uuid={} created={} audio_size={}",
                data.speechResult.senseId,
                uuid,
                data.speechResult.dateTimeUTC,
                data.audioData.length);

        final SpeechResultsKinesis.SpeechResultsData.Builder builder = SpeechResultsKinesis.SpeechResultsData.newBuilder();
        if (data.audioData.length > 0) {
            // store audio path
            final SpeechResultsKinesis.AudioData audioData = SpeechResultsKinesis.AudioData.newBuilder()
                    .setDataSize(data.audioData.length)
                    .setData(ByteString.copyFrom(data.audioData))
                    .build();

            final Long accountId = (data.speechResult.accountId.isPresent()) ? data.speechResult.accountId.get() : 0L;
            return builder
                    .setAccountId(accountId)
                    .setSenseId(data.speechResult.senseId)
                    .setCreated(data.speechResult.dateTimeUTC.getMillis())
                    .setAudioUuid(data.speechResult.audioIdentifier)
                    .setAudio(audioData)
                    .setAction(data.action)
                    .build();
        }

        // final Set<Number> confidences = SpeechUtils.wakewordsMapToDDBAttribute(data.speechResult.wakeWordsConfidence);
        final Long currentTimestamp = DateTime.now(DateTimeZone.UTC).getMillis();

        // set non-optional fields
        builder.setAudioUuid(data.speechResult.audioIdentifier)
                .setSenseId(data.speechResult.senseId)
                .setCreated(data.speechResult.dateTimeUTC.getMillis())
                .setUpdated(currentTimestamp)
                .setService(data.speechResult.service.toString())
                .setWakeId(data.speechResult.wakeWord.getId())
//                .setWakeConfidence(confidences)
                .setResult(data.speechResult.result.toString())
                .setAction(data.action);

        if (data.speechResult.accountId.isPresent()) {
            builder.setAccountId(data.speechResult.accountId.get());
        }

        if (data.speechResult.text.isPresent()) {
            builder.setText(data.speechResult.text.get());
        }

        if (data.speechResult.confidence.isPresent()) {
            builder.setConfidence(data.speechResult.confidence.get());
        }

        if (data.speechResult.handlerType.isPresent()) {
            builder.setHandlerType(data.speechResult.handlerType.get());
        }

        if (data.speechResult.s3ResponseKeyname.isPresent()) {
            builder.setS3Keyname(data.speechResult.s3ResponseKeyname.get());
        }
        if (data.speechResult.command.isPresent()) {
            builder.setCommand(data.speechResult.command.get());
        }

        if (data.speechResult.responseText.isPresent()) {
            builder.setResponseText(data.speechResult.responseText.get());
        }

        // set wake confidence
        for (final WakeWord word : WakeWord.values()) {
            if (word.equals(WakeWord.NULL)) {
                continue;
            }

            final String wordText = word.getWakeWordText();
            final float confidence = (data.speechResult.wakeWordsConfidence.containsKey(wordText)) ?
                    data.speechResult.wakeWordsConfidence.get(wordText) : 0.0f;
            builder.addWakeConfidence(confidence);
        }
        return builder.build();
    }
}
