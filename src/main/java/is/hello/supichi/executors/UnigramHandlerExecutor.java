package is.hello.supichi.executors;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import is.hello.supichi.commandhandlers.BaseHandler;
import is.hello.supichi.models.AnnotatedTranscript;
import is.hello.supichi.models.HandlerResult;
import is.hello.supichi.models.HandlerType;
import is.hello.supichi.models.VoiceRequest;
import is.hello.supichi.response.SupichiResponseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class UnigramHandlerExecutor implements HandlerExecutor {

    private Map<HandlerType, BaseHandler> availableHandlers = Maps.newConcurrentMap();
    private Map<String, HandlerType> commandToHandlerMap = Maps.newConcurrentMap();
    private Map<HandlerType, SupichiResponseType> responseBuilders = Maps.newConcurrentMap();

    private final static Logger LOGGER = LoggerFactory.getLogger(UnigramHandlerExecutor.class);

    public UnigramHandlerExecutor() {
    }


    @Override
    public HandlerResult handle(final VoiceRequest request) {
        final String transcript = request.transcript;
        final String[] unigrams = transcript.toLowerCase().split(" ");

        for (int i = 0; i < (unigrams.length - 1); i++) {
            final String commandText = String.format("%s %s", unigrams[i], unigrams[i+1]);
            LOGGER.debug("action=get-transcribed-command text={}", commandText);

            // TODO: command-parser
            final Optional<BaseHandler> optionalHandler = getHandler(transcript.toLowerCase());

            if (optionalHandler.isPresent()) {
                final BaseHandler handler = optionalHandler.get();
                LOGGER.debug("action=find-handler result=success handler={}", handler.getClass().toString());

                final AnnotatedTranscript annotatedTranscript = new AnnotatedTranscript.Builder().withTranscript(commandText).build();

                final HandlerResult executeResult = handler.executeCommand(annotatedTranscript, request);
                LOGGER.debug("action=execute-command result={}", executeResult.outcome().getValue());
                return executeResult;
            }
        }

        LOGGER.debug("action=fail-to-find-command account_id={} sense_id={} transcript={}", request.accountId, request.senseId, request.transcript);
        return HandlerResult.emptyResult();
    }

    @Override
    public HandlerExecutor register(final HandlerType handlerType, final BaseHandler baseHandler) {
        // Create in memory global map of commands
        for (final String command : baseHandler.getRelevantCommands()) {
            final HandlerType type = commandToHandlerMap.putIfAbsent(command, handlerType);
            if(type != null) {
                LOGGER.warn("warn=duplicate-command command={} handler={}", command, handlerType);
            }
        }

        availableHandlers.put(handlerType, baseHandler);
        responseBuilders.put(handlerType, baseHandler.responseType());
        return this;
    }


    private Optional<BaseHandler> getHandler(final String command) {
        if (commandToHandlerMap.containsKey(command)) {
            final HandlerType handlerType = commandToHandlerMap.get(command);
            if (availableHandlers.containsKey(handlerType)) {
                return Optional.of(availableHandlers.get(handlerType));
            }
        }
        return Optional.absent();
    }

    @Override
    public Map<HandlerType, SupichiResponseType> responseBuilders() {
        return responseBuilders;
    }

    @Override
    public Optional<BaseHandler> getHandler(AnnotatedTranscript annotatedTranscript) {
        return getHandler(annotatedTranscript.transcript);
    }
}
