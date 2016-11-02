package is.hello.supichi.response;

import com.amazonaws.services.s3.AmazonS3;
import com.google.api.client.util.Maps;
import is.hello.supichi.api.Response;
import is.hello.supichi.api.Speech;
import is.hello.supichi.models.HandlerResult;
import is.hello.supichi.models.responsebuilder.BuilderResponse;
import is.hello.supichi.models.responsebuilder.CurrentTimeResponseBuilder;
import is.hello.supichi.models.responsebuilder.DefaultResponseBuilder;
import is.hello.supichi.models.responsebuilder.ResponseUtils;
import is.hello.supichi.models.responsebuilder.TriviaResponseBuilder;
import is.hello.supichi.utils.AudioUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

/**
 * Created by ksg on 7/26/16
 */
public class S3ResponseBuilder implements SupichiResponseBuilder {
    private final static Logger LOGGER = LoggerFactory.getLogger(S3ResponseBuilder.class);

    private final AmazonS3 s3;
    private final String voiceService;
    private final String voiceName;
    private final Map<String, byte []> audioCache = Maps.newHashMap();
    private final Map<Speech.Equalizer, String> equalizerToS3Path = Maps.newHashMap();

    public S3ResponseBuilder(final AmazonS3 s3, final Map<Speech.Equalizer, String> equalizerToS3Path, final String voiceService, final String voiceName) {
        this.s3 = s3;
        this.equalizerToS3Path.putAll(equalizerToS3Path);
        this.voiceService = voiceService;
        this.voiceName = voiceName.equals("en-US_MichaelVoice") ? "MICHAEL" : "ALLISON";
    }

    /**
     * Creates a response
     * Format: [PB-size] + [response PB] + [audio-data]
     * @param result transcription result
     * @return bytes
     * @throws IOException
     */
    @Override
    public byte[] response(final Response.SpeechResponse.Result result,
                           final HandlerResult handlerResult,
                           final Speech.SpeechRequest request) {

        LOGGER.debug("action=create-response result={} handler={}", result.toString(), handlerResult.handlerType.toString());

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // get custom response text and audio
        BuilderResponse builderResponse; // ¡uh-oh! MUTATION
        switch(handlerResult.handlerType) {
            case TIME_REPORT:
                builderResponse = CurrentTimeResponseBuilder.response(handlerResult, voiceService, voiceName);
                break;
            case TRIVIA:
                builderResponse = TriviaResponseBuilder.response(handlerResult, voiceService, voiceName);
                break;
            default:
                builderResponse = DefaultResponseBuilder.response(result);
        }

        LOGGER.info("response-type={} eq={}", request.getResponse().name(), request.getEq().name());
        // get raw audio bytes from S3
        byte [] audioBytes = getAudio(builderResponse.s3Bucket, builderResponse.s3Filename, request.getEq());

        if (audioBytes.length == 0) {
            LOGGER.error("error=fail-to-audio-bytes action=return-UNKNOWN-error-response");
            builderResponse = DefaultResponseBuilder.response(Response.SpeechResponse.Result.UNKNOWN);
            audioBytes = getAudio(builderResponse.s3Bucket, builderResponse.s3Filename, request.getEq());
        }


        try {
            LOGGER.debug("action=return-audio-only-response size={}", audioBytes.length);
            if (request.getResponse().equals(Speech.AudioFormat.MP3)) {
                LOGGER.debug("action=convert-pcm-to-mp3 size={}", audioBytes.length);
                final AudioFormat audioFormat = AudioUtils.DEFAULT_AUDIO_FORMAT;
                final byte[] mp3Bytes = AudioUtils.encodePcmToMp3(new AudioUtils.AudioBytes(audioBytes, audioBytes.length, audioFormat));
                outputStream.write(mp3Bytes);
            } else {
                outputStream.write(audioBytes);
            }
        } catch (IOException exception) {
            LOGGER.error("action=response-builder-error_msg={}", exception.getMessage());
        }
        return outputStream.toByteArray();
    }


    private byte [] getAudio(final String s3Bucket, final String filename, final Speech.Equalizer eq) {
        final String cacheKey = String.format("%s_%s", eq.name(), filename);

        if (audioCache.containsKey(cacheKey)) {
            LOGGER.debug("action=found-audio-in-cache");
            return audioCache.get(cacheKey);
        }

        String finalS3Bucket = equalizerToS3Path.get(eq);
        if (!s3Bucket.isEmpty()) {
            finalS3Bucket += "/" + s3Bucket;
        }

        final byte [] audioBytes = ResponseUtils.getAudioFromS3(s3, finalS3Bucket, filename);
        if (audioBytes.length > 0) {
            audioCache.put(cacheKey, audioBytes);   // add to cache
        }

        return audioBytes;
    }

}
