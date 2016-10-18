package is.hello.speech.commandhandlers.results;

import com.google.common.base.Optional;
import is.hello.speech.models.HandlerResult;

/**
 * Created by ksg on 9/21/16
 */
public class GenericResult implements ResultInterface {

    public final Outcome outcome;
    public final Optional<String> errorText;
    public final Optional<String> responseText;


    private GenericResult(final Outcome outcome, final Optional<String> errorText, final Optional<String> responseText) {
        this.outcome = outcome;
        this.errorText = errorText;
        this.responseText = responseText;
    }

    public static GenericResult ok(final String responseText) {
        return new GenericResult(Outcome.OK, Optional.absent(), Optional.of(responseText));
    }

    public static GenericResult fail(final String errorText) {
        return new GenericResult(Outcome.FAIL, Optional.of(errorText), Optional.absent());
    }

    public static GenericResult failWithResponse(final String errorText, final String responseText) {
        return new GenericResult(Outcome.FAIL, Optional.of(errorText), Optional.of(responseText));
    }

    @Override
    public String responseText() {
        if (responseText.isPresent()) {
            return responseText.get();
        }
        return HandlerResult.EMPTY_STRING;
    }

    @Override
    public String commandText() {
        return null;
    }
}
