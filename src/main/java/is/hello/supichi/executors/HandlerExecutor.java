package is.hello.supichi.executors;

import com.google.common.base.Optional;
import is.hello.supichi.commandhandlers.BaseHandler;
import is.hello.supichi.models.AnnotatedTranscript;
import is.hello.supichi.models.HandlerResult;
import is.hello.supichi.models.HandlerType;
import is.hello.supichi.models.VoiceRequest;
import is.hello.supichi.response.SupichiResponseType;

import java.util.Map;

public interface HandlerExecutor {
    HandlerResult handle(final VoiceRequest request);
    HandlerExecutor register(HandlerType handlerType, BaseHandler baseHandler);
    Map<HandlerType, SupichiResponseType> responseBuilders();
    Optional<BaseHandler> getHandler(AnnotatedTranscript annotatedTranscript);
}
