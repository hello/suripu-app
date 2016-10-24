package is.hello.supichi.models.responsebuilder;

import is.hello.supichi.commandhandlers.results.Outcome;
import is.hello.supichi.models.HandlerResult;
import is.hello.supichi.api.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Created by ksg on 8/8/16
 */
public class TriviaResponseBuilder implements ResponseBuilderInterface{
    private static final Logger LOGGER = LoggerFactory.getLogger(TriviaResponseBuilder.class);

    // MUST-HAVES
    private static final String BUCKET_NAME = "TRIVIA/TRIVIA_INFO";
    private static final String FILENAME_PREFIX = "TRIVIA-GET_TRIVIA-TRIVIA_INFO";


    public static BuilderResponse response(final HandlerResult handlerResult, final String voiceService, final String voiceName) {
        String s3Bucket = BUCKET_NAME;
        String filename = FILENAME_PREFIX;
        final String responseText;

        if (handlerResult.outcome().equals(Outcome.OK) && handlerResult.fileMarker.isPresent()) {
            final String marker = handlerResult.fileMarker.get();
            filename += String.format("-%s-%s-%s-16k.wav", marker, voiceService, voiceName);
            responseText = handlerResult.responseText();

        } else {
            // return generic response
            s3Bucket = DefaultResponseBuilder.BUCKET_NAME;
            filename = DefaultResponseBuilder.DEFAULT_KEYNAMES.get(Response.SpeechResponse.Result.REJECTED);
            responseText = DefaultResponseBuilder.DEFAULT_TEXT.get(Response.SpeechResponse.Result.REJECTED);
        }

        return new BuilderResponse(s3Bucket, filename, responseText);
    }

}
