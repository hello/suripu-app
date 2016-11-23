package is.hello.supichi.models;

import com.google.common.base.Optional;
import is.hello.supichi.commandhandlers.results.GenericResult;
import is.hello.supichi.commandhandlers.results.Outcome;

/**
 * Created by ksg on 11/3/16
 */
public class GenericResponseText {
    public static String NO_PAIRED_SENSE_TEXT = "Sorry, you do not have an account paired. Please pair your account to Sense and try again.";
    public static String TRY_AGAIN_TEXT = "Sorry, your command cannot be processed. Please try again.";
    public static String COMMAND_REJECTED_TEXT =  "Sorry, your command is rejected";
    public static String UNKNOWN_TEXT = "Sorry, there was a problem. Please try again later.";
    public static String OK_TEXT = "Okay.";

    public static String alternativeResponseText(final Optional<GenericResult> result) {
        if (!result.isPresent()) {
            return UNKNOWN_TEXT;
        }

        final Outcome outcome = result.get().outcome;
        return (outcome.equals(Outcome.OK)) ? OK_TEXT : UNKNOWN_TEXT;
    }
}