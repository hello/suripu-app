package is.hello.supichi.models.responsebuilder;

import is.hello.supichi.models.HandlerResult;
import is.hello.supichi.api.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by ksg on 8/9/16
 */
public class DefaultResponseBuilder implements ResponseBuilderInterface {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultResponseBuilder.class);

    public static final String BUCKET_NAME = ""; // use base bucket

    public final static Map<Response.SpeechResponse.Result, String> DEFAULT_KEYNAMES = new HashMap<Response.SpeechResponse.Result, String>() {{
        put(Response.SpeechResponse.Result.OK, "default_ok-WATSON-ALLISON-16k.wav");
        put(Response.SpeechResponse.Result.REJECTED, "default_rejected-WATSON-ALLISON-16k.wav");
        put(Response.SpeechResponse.Result.TRY_AGAIN, "default_try_again-WATSON-ALLISON-16k.wav");
        put(Response.SpeechResponse.Result.UNKNOWN, "default_unknown-WATSON-ALLISON-16k.wav");
        put(Response.SpeechResponse.Result.UNPAIRED_SENSE, "default_unpaired-WATSON-ALLISON-16k.wav");
    }};

    public final static Map<Response.SpeechResponse.Result, String> DEFAULT_TEXT = new HashMap<Response.SpeechResponse.Result, String>() {{
        put(Response.SpeechResponse.Result.OK, "OK, it's done.");
        put(Response.SpeechResponse.Result.REJECTED, "Sorry, your command is rejected");
        put(Response.SpeechResponse.Result.TRY_AGAIN, "Sorry, your command cannot be processed. Please try again.");
        put(Response.SpeechResponse.Result.UNKNOWN, "Sorry, we've encountered an error. Please try again later.");
        put(Response.SpeechResponse.Result.UNPAIRED_SENSE, "Sorry, you do not have an account paired. Please pair your account to Sense and try again.");
    }};


    public static BuilderResponse response(final HandlerResult handlerResult, final String voiceService, final String voiceName) {
        return BuilderResponse.empty();
    }

    public static BuilderResponse response(final Response.SpeechResponse.Result result) {
        if (DEFAULT_KEYNAMES.containsKey(result)) {
            return new BuilderResponse(BUCKET_NAME, DEFAULT_KEYNAMES.get(result), DEFAULT_TEXT.get(result));
        }

        return new BuilderResponse(BUCKET_NAME,
                DEFAULT_KEYNAMES.get(Response.SpeechResponse.Result.UNKNOWN),
                DEFAULT_TEXT.get(Response.SpeechResponse.Result.UNKNOWN));
    }
}
