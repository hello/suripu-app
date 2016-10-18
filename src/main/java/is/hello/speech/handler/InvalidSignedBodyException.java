package is.hello.speech.handler;

/**
 * Created by ksg on 9/8/16
 */
public class InvalidSignedBodyException extends RuntimeException {
    InvalidSignedBodyException(final String message) {
        super(message);
    }
}
