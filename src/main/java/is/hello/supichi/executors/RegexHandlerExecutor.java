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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexHandlerExecutor implements HandlerExecutor {

    private Map<HandlerType, BaseHandler> availableHandlers = Maps.newConcurrentMap();
    private Map<String, HandlerType> commandToHandlerMap = Maps.newConcurrentMap();
    private Map<HandlerType, SupichiResponseType> responseBuilders = Maps.newConcurrentMap();

    private final static Logger LOGGER = LoggerFactory.getLogger(RegexHandlerExecutor.class);

    public RegexHandlerExecutor() {
    }


    @Override
    public HandlerResult handle(final VoiceRequest request) {
        // TODO: command-parser
        final String transcript = request.transcript;
        final Optional<BaseHandler> optionalHandler = getHandler(transcript);

        if (optionalHandler.isPresent()) {
            final BaseHandler handler = optionalHandler.get();
            LOGGER.debug("action=find-handler result=success handler={}", handler.getClass().toString());

            final AnnotatedTranscript annotatedTranscript = new AnnotatedTranscript.Builder().withTranscript(transcript).build();

            final HandlerResult executeResult = handler.executeCommand(annotatedTranscript, request);
            LOGGER.debug("action=execute-command result={}", executeResult.outcome().getValue());
            return executeResult;
        }

        LOGGER.debug("action=fail-to-find-command account_id={} sense_id={} transcript={}", request.accountId, request.senseId, transcript);
        return HandlerResult.emptyResult();
    }

    @Override
    public HandlerExecutor register(final HandlerType handlerType, final BaseHandler baseHandler) {
        // Create in memory global map of commandse
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

    @Override
    public Optional<BaseHandler> getHandler(final AnnotatedTranscript transcript) {
        return getHandler(transcript.transcript);
    }

    private Optional<BaseHandler> getHandler(String command) {
        Optional<BaseHandler> foundHandler = Optional.absent();
        for(final String pattern : commandToHandlerMap.keySet()) {
            final Pattern r = Pattern.compile(pattern);
            LOGGER.debug("Pattern {}, {}", pattern, commandToHandlerMap.get(pattern));
            Matcher m = r.matcher(command);
            if(m.find()) {
                final HandlerType handlerType = commandToHandlerMap.get(pattern);
                if (!foundHandler.isPresent() && availableHandlers.containsKey(handlerType)) {
                    foundHandler = Optional.of(availableHandlers.get(handlerType));
                }
            }
        }
        return foundHandler;
    }

    @Override
    public Map<HandlerType, SupichiResponseType> responseBuilders() {
        return responseBuilders;
    }
}
