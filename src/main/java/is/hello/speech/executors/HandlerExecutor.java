package is.hello.speech.executors;

import is.hello.speech.commandhandlers.BaseHandler;
import is.hello.speech.models.HandlerResult;
import is.hello.speech.models.HandlerType;
import is.hello.speech.models.VoiceRequest;
import is.hello.speech.response.SupichiResponseType;

import java.util.Map;

public interface HandlerExecutor {
    HandlerResult handle(final VoiceRequest request);
    HandlerExecutor register(HandlerType handlerType, BaseHandler baseHandler);
    Map<HandlerType, SupichiResponseType> responseBuilders();
}
