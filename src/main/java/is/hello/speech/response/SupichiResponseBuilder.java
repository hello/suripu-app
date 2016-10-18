package is.hello.speech.response;

import is.hello.speech.api.Response;
import is.hello.speech.api.Speech;
import is.hello.speech.models.HandlerResult;

public interface SupichiResponseBuilder {

    byte[] response(final Response.SpeechResponse.Result result,
                    final HandlerResult handlerResult,
                    final Speech.SpeechRequest request);
}
