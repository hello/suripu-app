package is.hello.supichi.commandhandlers.results;

import com.google.common.base.Optional;
import is.hello.supichi.models.HandlerResult;

/**
 * Created by ksg on 9/21/16
 */
public class GenericResult implements ResultInterface {

    public final Outcome outcome;
    public final Optional<String> errorText;
    public final Optional<String> responseText;
    public final Optional<String> url;


    private GenericResult(final Outcome outcome, final Optional<String> errorText, final Optional<String> responseText, final Optional<String> url) {
        this.outcome = outcome;
        this.errorText = errorText;
        this.responseText = responseText;
        this.url = url;
    }

    public static GenericResult ok(final String responseText) {
        return new GenericResult(Outcome.OK, Optional.absent(), Optional.of(responseText), Optional.absent());
    }

    public static GenericResult fail(final String errorText) {
        return new GenericResult(Outcome.FAIL, Optional.of(errorText), Optional.absent(), Optional.absent());
    }

    public static GenericResult failWithResponse(final String errorText, final String responseText) {
        return new GenericResult(Outcome.FAIL, Optional.of(errorText), Optional.of(responseText), Optional.absent());
    }

    public static GenericResult withMP3(final String text, final String url) {
        return new GenericResult(Outcome.OK, Optional.absent(), Optional.of(text), Optional.of(url));
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
