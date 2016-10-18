package is.hello.speech.models.responsebuilder;

import com.google.common.base.Optional;
import is.hello.speech.commandhandlers.results.Outcome;
import is.hello.speech.models.HandlerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static is.hello.speech.commandhandlers.ErrorText.NO_TIMEZONE;

/**
 * Created by ksg on 8/8/16
 */
public class CurrentTimeResponseBuilder implements ResponseBuilderInterface{

    private static final Logger LOGGER = LoggerFactory.getLogger(CurrentTimeResponseBuilder.class);

    // MUST-HAVES
    private static final String BUCKET_NAME = "TIME_REPORT/TIME";
    private static final String FILENAME_PREFIX = "TIME_REPORT-GET_TIME-TIME";

    // Builder-related
    private static final String TIMEZONE_ERROR_TEXT = "Sorry, we're not able to get the time. Please set your timezone in the app";
    private static final String TIME_ERROR_TEXT = "Sorry, I'm not able to determine the time right now. Please try again later.";
    private static final String TIME_ERROR_FILENAME_TEMPLATE = "-no_data-%s-%s-16k.wav";
    // TODO: create new file with no timezone message
    private static final String TIMEZONE_ERROR_FILENAME_TEMPLATE = "-no_data-%s-%s-16k.wav";

    private static final String RESPONSE_TEXT_FORMATTER = "The time is %s.";


    public static BuilderResponse response(final HandlerResult handlerResult, final String voiceService, final String voiceName) {

        String filename = FILENAME_PREFIX;
        final String responseText;

        final Outcome outcome = handlerResult.outcome();
        if (outcome.equals(Outcome.OK)) {
            final String timeString = handlerResult.responseText();
            filename += String.format("-%s-%s-%s-16k.wav", timeString, voiceService, voiceName);
            responseText = String.format(RESPONSE_TEXT_FORMATTER, timeString);

        } else {

            // error
            final Optional<String> optionalErrorText = handlerResult.optionalErrorText();
            if (optionalErrorText.isPresent() && optionalErrorText.get().equals(NO_TIMEZONE)) {
                responseText = TIMEZONE_ERROR_TEXT;
                filename += String.format(TIMEZONE_ERROR_FILENAME_TEMPLATE, voiceService, voiceName);
            } else {
                responseText = TIME_ERROR_TEXT;
                filename += String.format(TIME_ERROR_FILENAME_TEMPLATE, voiceService, voiceName);
            }
        }

        return new BuilderResponse(BUCKET_NAME, filename, responseText);
    }
}
