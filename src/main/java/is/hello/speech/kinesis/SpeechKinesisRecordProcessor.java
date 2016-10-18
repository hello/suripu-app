package is.hello.speech.kinesis;


import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.kinesis.clientlibrary.exceptions.InvalidStateException;
import com.amazonaws.services.kinesis.clientlibrary.exceptions.ShutdownException;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.IRecordProcessor;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.IRecordProcessorCheckpointer;
import com.amazonaws.services.kinesis.clientlibrary.types.ShutdownReason;
import com.amazonaws.services.kinesis.model.Record;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.SSEAwsKeyManagementParams;
import com.google.api.client.util.Maps;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hello.suripu.core.speech.interfaces.SpeechResultIngestDAO;
import com.hello.suripu.core.speech.interfaces.SpeechTimelineIngestDAO;
import com.hello.suripu.core.speech.models.Result;
import com.hello.suripu.core.speech.models.SpeechResult;
import com.hello.suripu.core.speech.models.SpeechTimeline;
import com.hello.suripu.core.speech.models.SpeechToTextService;
import com.hello.suripu.core.speech.models.WakeWord;
import is.hello.speech.api.SpeechResultsKinesis;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

/**
 * Created by ksg on 8/11/16
 */
public class SpeechKinesisRecordProcessor implements IRecordProcessor {
    private final Logger LOGGER = LoggerFactory.getLogger(SpeechKinesisRecordProcessor.class);

    private final AmazonS3 s3;
    private final String s3Bucket;
    private final SSEAwsKeyManagementParams s3SSEKey;
    private final SpeechTimelineIngestDAO speechTimelineIngestDAO;
    private final SpeechResultIngestDAO speechResultIngestDAO;

    public SpeechKinesisRecordProcessor(final String s3Bucket,
                                        final AmazonS3 s3,
                                        final SSEAwsKeyManagementParams s3SSEKey,
                                        final SpeechTimelineIngestDAO speechTimelineIngestDAO,
                                        final SpeechResultIngestDAO speechResultIngestDAO) {
        this.s3Bucket = s3Bucket;
        this.s3 = s3;
        this.s3SSEKey = s3SSEKey;
        this.speechTimelineIngestDAO = speechTimelineIngestDAO;
        this.speechResultIngestDAO = speechResultIngestDAO;
    }

    @Override
    public void initialize(String shardId) {}

    @Override
    public void processRecords(List<Record> records, IRecordProcessorCheckpointer checkpointer) {
        LOGGER.debug("info=number-of-records size={}", records.size());
        int numAudio = 0;
        for (final Record record : records) {
            final SpeechResultsKinesis.SpeechResultsData speechResultsData;
            final String sequenceNumber = record.getSequenceNumber();
            try {
                speechResultsData = SpeechResultsKinesis.SpeechResultsData.parseFrom(record.getData().array());

                switch (speechResultsData.getAction()) {
                    case PUT_ITEM:
                        // put new speech results
                        saveTranscriptionResult(speechResultsData, sequenceNumber);
                        break;
                    case UPDATE_ITEM:
                        // update speech results with commands, handlers etc.
                        updateSpeechResult(speechResultsData, sequenceNumber);
                        break;
                    case TIMELINE:
                        if (speechResultsData.hasAudio() && speechResultsData.getAudio().getDataSize() > 0) {
                            // save audio and speech timeline
                            saveTimeline(speechResultsData, sequenceNumber);
                            final boolean saved = saveAudio(speechResultsData, sequenceNumber);
                            //TODO: remove after debug
                            LOGGER.debug("action=save-audio success={} sense_id={}, uuid={}, created={}",
                                    saved, speechResultsData.getSenseId(), speechResultsData.getAudioUuid(),
                                    speechResultsData.getCreated());
                            numAudio += (saved) ? 1 : 0;
                        }
                }
            } catch (InvalidProtocolBufferException e) {
                LOGGER.error("error= fail-to-decode-speech-protobuf error_msg={} sequence_number={} partition_key={}",
                        e.getMessage(), record.getSequenceNumber(), record.getPartitionKey());
            } catch (IllegalArgumentException e) {
                LOGGER.error("error=fail-to-decrypt-speech-result-data data={} error_msg={} sequence_number={} partition_key={}",
                        record.getData().array(), e.getMessage(), record.getSequenceNumber(), record.getPartitionKey());
            }
        }

        LOGGER.debug("audio_saved={}", numAudio);

        try {
            checkpointer.checkpoint();
            LOGGER.debug("action=checkpoint-success");
        } catch (InvalidStateException e) {
            LOGGER.error("error=checkpoint-fail error_msg={}", e.getMessage());
        } catch (ShutdownException e) {
            LOGGER.error("error=received-shutdown-command-at-checkpoint action=bailing error_msg={}", e.getMessage());
        }

    }

    @Override
    public void shutdown(IRecordProcessorCheckpointer checkpointer, ShutdownReason reason) {
        LOGGER.info("action=speech-kinesis-processor-shutting-down reason={}", reason);
        if (reason.equals(ShutdownReason.ZOMBIE)) {
            try {
                checkpointer.checkpoint();
            } catch (Exception e) {
                LOGGER.error("error=fail-to-checkpoint-before-shutting-down error_msg={}", e.getMessage());
            }
        }
    }

    private void updateSpeechResult(final SpeechResultsKinesis.SpeechResultsData speechResultsData, final String sequenceNumber) {
        final SpeechResult data = kinesisDataToSpeechResult(speechResultsData);
        final Boolean updated = speechResultIngestDAO.updateItem(data);
        LOGGER.debug("action=speech-result-update success={} uuid={} sequence_number={}", updated, speechResultsData.getAudioUuid(), sequenceNumber);
    }


    private void saveTranscriptionResult(final SpeechResultsKinesis.SpeechResultsData speechResultsData, final String sequenceNumber) {
        final SpeechResult data = kinesisDataToSpeechResult(speechResultsData);
        final Boolean saved = speechResultIngestDAO.putItem(data);
        LOGGER.debug("action=speech-result-put success={} uuid={} sequence_number={}", saved, speechResultsData.getAudioUuid(), sequenceNumber);
    }

    private SpeechResult kinesisDataToSpeechResult(final SpeechResultsKinesis.SpeechResultsData speechResultsData) {

        final SpeechResult.Builder builder = new SpeechResult.Builder()
                .withAudioIndentifier(speechResultsData.getAudioUuid())
                .withService(SpeechToTextService.fromString(speechResultsData.getService()))
                .withWakeWord(WakeWord.fromInteger(speechResultsData.getWakeId()))
                .withDateTimeUTC(new DateTime(speechResultsData.getCreated(), DateTimeZone.UTC))
                .withUpdatedUTC(new DateTime(speechResultsData.getUpdated(), DateTimeZone.UTC));

        if (speechResultsData.hasResult()) {
            builder.withResult(Result.fromString(speechResultsData.getResult()));
        }

        if (speechResultsData.hasConfidence()) {
            builder.withConfidence(speechResultsData.getConfidence());
        }
        if (speechResultsData.hasText()) {
            builder.withText(speechResultsData.getText());
        }

        if (speechResultsData.hasHandlerType()) {
            builder.withHandlerType(speechResultsData.getHandlerType());
        }

        if(speechResultsData.hasS3Keyname()) {
            builder.withS3Keyname(speechResultsData.getS3Keyname());
        }

        if(speechResultsData.hasCommand()) {
            builder.withCommand(speechResultsData.getCommand());
        }

        if(speechResultsData.hasResponseText()) {
            builder.withResponseText(speechResultsData.getResponseText());
        }

        // create wake word confidences kinesis message
        final List<Float> confidences = speechResultsData.getWakeConfidenceList();
        final Map<String, Float> wakeWordConfidence = Maps.newHashMap();
        for (final WakeWord word : WakeWord.values()) {
            if (word.equals(WakeWord.NULL)) {
                continue;
            }
            wakeWordConfidence.put(word.getWakeWordText(), confidences.get(word.getId() - 1)); // -1 because WakeWord.NULL is 0
        }
        builder.withWakeWordsConfidence(wakeWordConfidence);

        return builder.build();
    }

    private void saveTimeline(final SpeechResultsKinesis.SpeechResultsData speechResultsData, final String sequenceNumber) {

        final Long accountId = speechResultsData.getAccountId();
        final String senseId = speechResultsData.getSenseId();

        final SpeechTimeline speechTimeline = new SpeechTimeline(
                accountId,
                new DateTime(speechResultsData.getCreated(), DateTimeZone.UTC),
                senseId,
                speechResultsData.getAudioUuid());

        try {
            final boolean saved = speechTimelineIngestDAO.putItem(speechTimeline);
            LOGGER.debug("action=save-speech-timeline success={} sense_id={} account_id={} sequence_number={}",
                    saved, accountId, senseId, sequenceNumber);
        } catch (AmazonServiceException ase) {
            LOGGER.error("error=aws-service-exception status={} error_msg={} action=exiting sequence_number={}",
                    ase.getStatusCode(), ase.getMessage(), sequenceNumber);
            try {
                Thread.sleep(2000L);
            } catch (InterruptedException ignored) {
            }
            System.exit(1);
        } catch (AmazonClientException ace) {
            LOGGER.error("error=aws-client-exception error_msg={} sequence_number={}", ace.getMessage(), sequenceNumber);
        }
    }

    private boolean saveAudio(final SpeechResultsKinesis.SpeechResultsData speechResultsData, final String sequenceNumber) {

        LOGGER.debug("action=save-sense-upload-audio account_id={} sense_id={} audio_size={}",
                speechResultsData.getAccountId(),
                speechResultsData.getSenseId(),
                speechResultsData.getAudio().getDataSize());

        final byte[] audioBytes = speechResultsData.getAudio().getData().toByteArray();

        final ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        metadata.setContentLength(audioBytes.length);

        final String keyName = String.format("%s.raw", speechResultsData.getAudioUuid());

        try {
            s3.putObject(new PutObjectRequest(s3Bucket, keyName, new ByteArrayInputStream(audioBytes), metadata).withSSEAwsKeyManagementParams(s3SSEKey));
        } catch (AmazonServiceException ase) {
            LOGGER.error("error=aws-s3-service-exception status={} error_msg={} sequence_number={}",
                    ase.getStatusCode(), ase.getMessage(), sequenceNumber);
        } catch (AmazonClientException ace) {
            LOGGER.error("error=aws-s3-client-exception error_msg={} sequence_number={}",
                    ace.getMessage(), sequenceNumber);
        }

        return true;
    }
}
