package is.hello.supichi.handler;

/**
 * Created by ksg on 9/12/16
 */
public class InvalidSignatureException extends RuntimeException {
    InvalidSignatureException(final String message) {
        super(message);
    }
}
