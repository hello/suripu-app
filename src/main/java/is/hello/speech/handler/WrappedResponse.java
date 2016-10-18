package is.hello.speech.handler;

import java.util.Optional;

public class WrappedResponse {

    private final byte[] content;
    private final Optional<RequestError> error;

    private WrappedResponse(final byte[] content, final RequestError error) {
        this.content = content;
        this.error = Optional.ofNullable(error);
    }

    public static WrappedResponse error(final RequestError error) {
        return new WrappedResponse(new byte[]{}, error);
    }

    public static WrappedResponse ok(final byte[] content) {
        return new WrappedResponse(content, null);
    }

    public static WrappedResponse empty() {
        return new WrappedResponse(new byte[]{}, null);
    }

    public boolean hasError() {
        return error.isPresent();
    }

    public byte[] content() {
        return content;
    }
}
