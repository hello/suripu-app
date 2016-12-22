package is.hello.supichi.handler;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.hello.suripu.core.models.device.v2.DeviceProcessor;
import com.hello.suripu.core.speech.models.Result;
import com.hello.suripu.core.speech.models.SpeechResult;
import com.hello.suripu.core.speech.models.SpeechToTextService;
import com.hello.suripu.core.speech.models.WakeWord;
import is.hello.supichi.api.Response;
import is.hello.supichi.api.Speech;
import is.hello.supichi.api.SpeechResultsKinesis;
import is.hello.supichi.clients.InstrumentedSpeechClient;
import is.hello.supichi.commandhandlers.results.GenericResult;
import is.hello.supichi.commandhandlers.results.Outcome;
import is.hello.supichi.executors.HandlerExecutor;
import is.hello.supichi.kinesis.SpeechKinesisProducer;
import is.hello.supichi.models.GenericResponseText;
import is.hello.supichi.models.HandlerResult;
import is.hello.supichi.models.HandlerType;
import is.hello.supichi.models.SpeechServiceResult;
import is.hello.supichi.models.VoiceRequest;
import is.hello.supichi.response.SupichiResponseBuilder;
import is.hello.supichi.response.SupichiResponseType;
import is.hello.supichi.utils.AudioUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.StringTokenizer;
import java.util.UUID;

import static com.codahale.metrics.MetricRegistry.name;
import static is.hello.supichi.commandhandlers.ErrorText.ERROR_NO_PAIRED_SENSE;

public class AudioRequestHandler {

    private static Logger LOGGER = LoggerFactory.getLogger(AudioRequestHandler.class);
    private static String SNOOZE_STRING = "snooze";
    private static String STOP_STRING = "stop";
    private static String GOODNIGHT_STRING = "goodnight";

    private final InstrumentedSpeechClient speechClient;
    private final SignedBodyHandler signedBodyHandler;
    private final HandlerExecutor handlerExecutor;

    private final DeviceProcessor deviceProcessor;

    private final SpeechKinesisProducer speechKinesisProducer;
    private final Map<SupichiResponseType, SupichiResponseBuilder> responseBuilders;
    private final Map<HandlerType, SupichiResponseType> handlerMap;

    private static final byte[] EMPTY_BYTE = new byte[0];

    private final MetricRegistry metrics;
    private Meter commandOK;
    private Meter commandFail;
    private Meter commandTryAgain;
    private Meter commandRejected;
    private Meter commandRejectSingleWord;
    private Meter requestInvalidBody;
    private Meter requestInvalidSignature;
    private Meter transcriptFail;

    public AudioRequestHandler(final InstrumentedSpeechClient speechClient,
                               final SignedBodyHandler signedBodyHandler,
                               final HandlerExecutor handlerExecutor,
                               final SpeechKinesisProducer speechKinesisProducer,
                               final Map<SupichiResponseType, SupichiResponseBuilder> responseBuilders,
                               final Map<HandlerType, SupichiResponseType> handlerMap,
                               final DeviceProcessor deviceProcessor,
                               final MetricRegistry metricRegistry
                               ) {
        this.speechClient = speechClient;
        this.signedBodyHandler = signedBodyHandler;
        this.handlerExecutor = handlerExecutor;
        this.speechKinesisProducer = speechKinesisProducer;
        this.responseBuilders = responseBuilders;
        this.handlerMap = handlerMap;
        this.deviceProcessor = deviceProcessor;

        this.metrics = metricRegistry;
        this.commandOK = metrics.meter(name(AudioRequestHandler.class, "command-ok"));
        this.commandFail = metrics.meter(name(AudioRequestHandler.class, "command-fail"));
        this.commandTryAgain = metrics.meter(name(AudioRequestHandler.class, "command-try-again"));
        this.commandRejected = metrics.meter(name(AudioRequestHandler.class, "command-rejected"));
        this.commandRejectSingleWord = metrics.meter(name(AudioRequestHandler.class, "command-rejected-single-word"));
        this.requestInvalidBody = metrics.meter(name(AudioRequestHandler.class, "invalid-body"));
        this.requestInvalidSignature = metrics.meter(name(AudioRequestHandler.class, "invalid-signature"));
        this.transcriptFail = metrics.meter(name(AudioRequestHandler.class, "transcript-fail"));
    }

    public WrappedResponse handle(final RawRequest rawRequest) {
        LOGGER.debug("action=received-bytes size={} sense_id={}", rawRequest.signedBody().length, rawRequest.senseId());

        // parse audio and protobuf
        final UploadData uploadData;
        try {
            uploadData = signedBodyHandler.extractUploadData(rawRequest.senseId(), rawRequest.signedBody());
        } catch (InvalidSignedBodyException e) {
            LOGGER.error("error=invalid-signed-body sense_id={} msg={}", rawRequest.senseId(), e.getMessage());
            this.requestInvalidBody.mark(1);
            return WrappedResponse.error(RequestError.INVALID_BODY);
        } catch(InvalidSignatureException e) {
            LOGGER.error("error=invalid-signature sense_id={} msg={}", rawRequest.senseId(), e.getMessage());
            this.requestInvalidSignature.mark(1);
            return WrappedResponse.error(RequestError.INVALID_SIGNATURE);
        }

        LOGGER.debug("action=get-pb-values word={} confidence={}", uploadData.request.getWord(), uploadData.request.getConfidence());
        final byte[] body = uploadData.audioBody;

        if(body.length == 0) {
            return WrappedResponse.error(RequestError.EMPTY_BODY);
        }

        HandlerResult executeResult = HandlerResult.emptyResult();

        // check for primary user account-id
        final Optional<Long> optionalPrimaryAccount = deviceProcessor.primaryAccount(rawRequest.senseId());
        if (!optionalPrimaryAccount.isPresent()) {
            LOGGER.error("error=no-paired-sense-found sense_id={}", rawRequest.senseId());
            executeResult = new HandlerResult(HandlerType.NONE, "", GenericResult.failWithResponse(ERROR_NO_PAIRED_SENSE, GenericResponseText.NO_PAIRED_SENSE_TEXT));
            final byte[] content = responseBuilders.get(SupichiResponseType.WATSON).response(Response.SpeechResponse.Result.UNPAIRED_SENSE, executeResult, uploadData.request);
            return WrappedResponse.ok(content);
        }

        final Long accountId = optionalPrimaryAccount.get();
        final String senseId = rawRequest.senseId();
        LOGGER.debug("action=get-speech-audio sense_id={} primary_account_id={} response_type={}", senseId, accountId, uploadData.request.getResponse());

        // save audio to Kinesis
        final String audioUUID = UUID.randomUUID().toString();
        final DateTime speechCreated = DateTime.now(DateTimeZone.UTC);

        SpeechResult.Builder builder = new SpeechResult.Builder();
        builder.withAccountId(accountId)
                .withSenseId(rawRequest.senseId())
                .withAudioIndentifier(audioUUID)
                .withDateTimeUTC(speechCreated);
        speechKinesisProducer.addResult(builder.build(), SpeechResultsKinesis.SpeechResultsData.Action.TIMELINE, body);

        // return empty bytes for certain wakeword
        final Speech.Keyword keyword = uploadData.request.getWord();
        final WakeWord wakeWord = WakeWord.fromString(keyword.name());
        final Map<String, Float> wakeWordConfidence = setWakeWordConfidence(wakeWord, (float) uploadData.request.getConfidence());
        builder.withUpdatedUTC(DateTime.now(DateTimeZone.UTC))
                .withWakeWord(wakeWord)
                .withWakeWordsConfidence(wakeWordConfidence)
                .withService(SpeechToTextService.GOOGLE);


        if (keyword.equals(Speech.Keyword.STOP) || keyword.equals(Speech.Keyword.SNOOZE)) {
            LOGGER.debug("action=encounter-STOP-SNOOZE keyword={}", keyword);
            builder.withResult(Result.OK);
            speechKinesisProducer.addResult(builder.build(), SpeechResultsKinesis.SpeechResultsData.Action.PUT_ITEM, EMPTY_BYTE);

            return WrappedResponse.silence();
        }

        try {
            // convert audio: ADPCM to 16-bit 16k PCM
            LOGGER.debug("action=start-adpcm-pcm-conversion sense_id={} input_size={}", senseId, body.length);

            final byte[] decoded = AudioUtils.decodeADPShitMAudio(body);

            LOGGER.debug("action=done-adpcm-pcm-conversion sense_id={} output_size={}", senseId, decoded.length);

            // send speech to google
            final SpeechServiceResult resp = speechClient.stream(rawRequest.senseId(), decoded, uploadData.request.getSamplingRate());


            if (!resp.getTranscript().isPresent()) {
                LOGGER.warn("action=google-transcript-failed final_result=try_again sense_id={} account_id={} response=silence",
                        senseId, accountId);

                this.transcriptFail.mark(1);
                builder.withUpdatedUTC(DateTime.now(DateTimeZone.UTC))
                        .withResponseText(GenericResponseText.TRY_AGAIN_TEXT)
                        .withResult(Result.TRY_AGAIN);
                speechKinesisProducer.addResult(builder.build(), SpeechResultsKinesis.SpeechResultsData.Action.PUT_ITEM, EMPTY_BYTE);

                return WrappedResponse.silence();
            }

            // save transcript results to Kinesis
            final String transcribedText = resp.getTranscript().get();
            builder.withUpdatedUTC(DateTime.now(DateTimeZone.UTC))
                    .withConfidence(resp.getConfidence())
                    .withText(transcribedText);


            // reject command if there's only a single word that is not stop/snooze
            final StringTokenizer tokenizer = new StringTokenizer(transcribedText);
            if (tokenizer.countTokens() == 1) {
                final String singleWord = tokenizer.nextToken();
                if (!singleWord.contains(SNOOZE_STRING) && !singleWord.contains(STOP_STRING) && !singleWord.contains(GOODNIGHT_STRING)) {
                    this.commandRejectSingleWord.mark(1);
                    builder.withUpdatedUTC(DateTime.now(DateTimeZone.UTC))
                            .withResponseText(GenericResponseText.COMMAND_REJECTED_TEXT)
                            .withResult(Result.REJECTED);
                    speechKinesisProducer.addResult(builder.build(), SpeechResultsKinesis.SpeechResultsData.Action.PUT_ITEM, EMPTY_BYTE);

                    LOGGER.error("error=command-rejected reason=single-word final_result=rejected sense_id={} account_id={} response=silence",
                            senseId, accountId);
                    return WrappedResponse.silence();
                }
            }

            // try to execute text command
            final VoiceRequest voiceRequest = new VoiceRequest(rawRequest.senseId(), accountId, transcribedText, rawRequest.ipAddress());
            executeResult = handlerExecutor.handle(voiceRequest);

            final SupichiResponseType responseType = handlerMap.getOrDefault(executeResult.handlerType, SupichiResponseType.STATIC);
            final SupichiResponseBuilder responseBuilder = responseBuilders.get(responseType);

            if (!executeResult.handlerType.equals(HandlerType.NONE)) {
                // save OK speech result
                Result commandResult = Result.OK;
                if (!executeResult.responseText().isEmpty()) {
                    commandResult = executeResult.outcome().equals(Outcome.OK) ? Result.OK : Result.REJECTED;
                }

                if (commandResult.equals(Result.OK)) {
                    this.commandOK.mark(1);
                } else {
                    this.commandFail.mark(1);
                }

                builder.withUpdatedUTC(DateTime.now(DateTimeZone.UTC))
                        .withCommand(executeResult.command)
                        .withHandlerType(executeResult.handlerType.value)
                        .withResponseText(executeResult.responseText())
                        .withResult(commandResult);
                speechKinesisProducer.addResult(builder.build(), SpeechResultsKinesis.SpeechResultsData.Action.PUT_ITEM, EMPTY_BYTE);

                LOGGER.info("action=command-processed-building-response final_result={} sense_id={} account_id={} command={} response={}",
                        commandResult.name(), senseId, accountId, executeResult.command, executeResult.responseText().replace(" ", "-"));

                final byte[] content = responseBuilder.response(Response.SpeechResponse.Result.OK, executeResult, uploadData.request);
                return WrappedResponse.ok(content);
            }

            // save TRY_AGAIN speech result
            this.commandTryAgain.mark(1);
            builder.withUpdatedUTC(DateTime.now(DateTimeZone.UTC))
                    .withResponseText(GenericResponseText.TRY_AGAIN_TEXT)
                    .withResult(Result.TRY_AGAIN);
            speechKinesisProducer.addResult(builder.build(), SpeechResultsKinesis.SpeechResultsData.Action.PUT_ITEM, EMPTY_BYTE);

            LOGGER.info("action=command-processed-no-handler-found final_result=try-again sense_id={} account_id={} response=generic-try-again-text",
                    senseId, accountId);

            final byte[] content = responseBuilder.response(Response.SpeechResponse.Result.TRY_AGAIN, executeResult, uploadData.request);
            return WrappedResponse.ok(content);

        } catch (Exception e) {
            LOGGER.error("action=streaming error={}", e.getMessage());
        }

        // no text or command found, save REJECT result
        final String responseText = executeResult.responseText().isEmpty() ? GenericResponseText.UNKNOWN_TEXT : executeResult.responseText();
        this.commandRejected.mark(1);
        builder.withUpdatedUTC(DateTime.now(DateTimeZone.UTC))
                .withResponseText(responseText)
                .withResult(Result.REJECTED);
        speechKinesisProducer.addResult(builder.build(), SpeechResultsKinesis.SpeechResultsData.Action.PUT_ITEM, EMPTY_BYTE);

        LOGGER.info("action=nothing-processed final_result=rejected sense_id={} account_id={} response={}",
                senseId, accountId, responseText.replace(" ", "-"));

        final byte[] content = responseBuilders.get(SupichiResponseType.STATIC).response(Response.SpeechResponse.Result.REJECTED, executeResult, uploadData.request);
        return WrappedResponse.ok(content);
    }

    private Map<String, Float> setWakeWordConfidence(final WakeWord wakeWord, final Float confidence) {
        final Map<String, Float> wakeWordConfidence = Maps.newHashMap();
        for (final WakeWord word : WakeWord.values()) {
            if (word.equals(WakeWord.NULL)) {
                continue;
            }
            if (word.equals(wakeWord)) {
                wakeWordConfidence.put(wakeWord.getWakeWordText(), confidence);
            } else {
                wakeWordConfidence.put(word.getWakeWordText(), 0.0f);
            }
        }
        return wakeWordConfidence;
    }
}
