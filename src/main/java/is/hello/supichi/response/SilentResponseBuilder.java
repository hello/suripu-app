package is.hello.supichi.response;

import is.hello.supichi.api.Response;
import is.hello.supichi.api.Speech;
import is.hello.supichi.handler.WrappedResponse;
import is.hello.supichi.models.HandlerResult;

/**
 * Created by ksg on 11/3/16
 */
public class SilentResponseBuilder implements SupichiResponseBuilder {
    @Override
    public byte[] response(Response.SpeechResponse.Result result, HandlerResult handlerResult, Speech.SpeechRequest request) {
        return WrappedResponse.silentBytes();
    }
}
