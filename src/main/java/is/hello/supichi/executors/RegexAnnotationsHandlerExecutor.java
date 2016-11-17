package is.hello.supichi.executors;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hello.suripu.core.db.TimeZoneHistoryDAODynamoDB;
import com.hello.suripu.core.models.TimeZoneHistory;
import is.hello.supichi.commandhandlers.BaseHandler;
import is.hello.supichi.models.AnnotatedTranscript;
import is.hello.supichi.models.Annotator;
import is.hello.supichi.models.HandlerResult;
import is.hello.supichi.models.HandlerType;
import is.hello.supichi.models.VoiceRequest;
import is.hello.supichi.response.SupichiResponseType;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexAnnotationsHandlerExecutor implements HandlerExecutor {

    private Map<HandlerType, BaseHandler> availableHandlers = Maps.newConcurrentMap();
    private Map<Pattern, HandlerType> commandToHandlerMap = Maps.newConcurrentMap();
    private Map<HandlerType, SupichiResponseType> responseBuilders = Maps.newConcurrentMap();

    private final TimeZoneHistoryDAODynamoDB timeZoneHistoryDAODynamoDB;

    private final static Logger LOGGER = LoggerFactory.getLogger(RegexAnnotationsHandlerExecutor.class);

    public RegexAnnotationsHandlerExecutor(final TimeZoneHistoryDAODynamoDB timeZoneHistoryDAODynamoDB) {
        this.timeZoneHistoryDAODynamoDB = timeZoneHistoryDAODynamoDB;
    }


    @Override
    public HandlerResult handle(final VoiceRequest request) {
        // TODO: command-parser

        final Long accountId = request.accountId;
        final String transcript = request.transcript;

        // get user TimeZone
        final Optional<TimeZoneHistory> timeZoneHistoryOptional = timeZoneHistoryDAODynamoDB.getCurrentTimeZone(accountId);
        final Optional<TimeZone> timeZone;// = DateTimeZone.forID("America/Los_Angeles").toTimeZone();
        if (timeZoneHistoryOptional.isPresent()) {
            timeZone = Optional.of(DateTimeZone.forID(timeZoneHistoryOptional.get().timeZoneId).toTimeZone());
        } else {
            timeZone = Optional.absent();
        }

        // extract entities
        final AnnotatedTranscript annotatedTranscript = Annotator.get(transcript, timeZone);

        final Optional<BaseHandler> optionalHandler = getHandler(annotatedTranscript);

        if (optionalHandler.isPresent()) {
            final BaseHandler handler = optionalHandler.get();
            LOGGER.debug("action=find-handler sense_id={} account_id={} result=success handler={}",
                    request.senseId, accountId, handler.getClass().toString());

            final HandlerResult executeResult = handler.executeCommand(annotatedTranscript, request);
            LOGGER.info("action=execute-command sense_id={} account_id={} result={} response={}",
                    request.senseId, accountId, executeResult.outcome().getValue(),
                    executeResult.responseText().replace(" ", "-"));

            return executeResult;
        }

        LOGGER.debug("action=fail-to-find-command transcript={} sense_id={} transcript={}", accountId, request.senseId, transcript);
        return HandlerResult.emptyResult();
    }

    @Override
    public HandlerExecutor register(final HandlerType handlerType, final BaseHandler baseHandler) {
        // Create in memory global map of commands
        for (final String command : baseHandler.getRelevantCommands()) {
            final Pattern regexPattern = Pattern.compile(command);
            final HandlerType type = commandToHandlerMap.putIfAbsent(regexPattern, handlerType);
            if(type != null) {
                LOGGER.warn("warn=duplicate-command command={} handler={}", command, handlerType);
            }
        }

        availableHandlers.put(handlerType, baseHandler);
        responseBuilders.put(handlerType, baseHandler.responseType());
        return this;
    }

    public Optional<BaseHandler> getHandler(final AnnotatedTranscript annotatedTranscript) {

        // Find a suitable handler via text
        final String command = annotatedTranscript.transcript.toLowerCase();

        // final Map<BaseHandler, Integer> possibleHandlers = Maps.newHashMap();
        final List<BaseHandler> possibleHandlers = Lists.newArrayList();
        for(final Pattern pattern : commandToHandlerMap.keySet()) {

            final HandlerType handlerType = commandToHandlerMap.get(pattern);
            Matcher m = pattern.matcher(command);
            if(m.find()) {
                LOGGER.debug("match_pattern={}, handler_type={}", pattern, handlerType);
                if (availableHandlers.containsKey(handlerType)) {
                    final BaseHandler matchedHandler = availableHandlers.get(handlerType);
                    if (!possibleHandlers.contains(matchedHandler)) {
                        possibleHandlers.add(matchedHandler);
                    }
//                    possibleHandlers.putIfAbsent(matchedHandler, 0);
//                    possibleHandlers.replace(matchedHandler, possibleHandlers.get(matchedHandler) + 1);
                }
            }
        }

        if (possibleHandlers.size() == 1) {
            return Optional.of(possibleHandlers.get(0));
        }
        else if (possibleHandlers.isEmpty()) {
            return Optional.absent();
        }

        // additional handler scoring based on annotatedTranscript matching
        int maxScore = 0;
        BaseHandler winner = possibleHandlers.get(0);
        for (final BaseHandler handler : possibleHandlers) {
            final int score = handler.matchAnnotations(annotatedTranscript);
            if (score > maxScore) {
                LOGGER.debug("action=score-annotations prev_handler={} prev_score={} new_handler={} new_score={}",
                        winner.getClass(), maxScore, handler.getClass(), score);
                winner = handler;
                maxScore = score;
            }
        }
        LOGGER.debug("action=get-handler final_handler={} final_score={}", winner.getClass(), maxScore);
        return Optional.of(winner);
    }

    @Override
    public Map<HandlerType, SupichiResponseType> responseBuilders() {
        return responseBuilders;
    }
}
