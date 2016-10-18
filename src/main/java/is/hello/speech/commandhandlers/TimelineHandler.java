package is.hello.speech.commandhandlers;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import is.hello.speech.db.SpeechCommandDAO;
import is.hello.speech.commandhandlers.results.GenericResult;
import is.hello.speech.models.AnnotatedTranscript;
import is.hello.speech.models.HandlerResult;
import is.hello.speech.models.HandlerType;
import is.hello.speech.models.SpeechCommand;
import is.hello.speech.models.VoiceRequest;
import is.hello.speech.response.SupichiResponseType;

import java.util.Map;

import static is.hello.speech.commandhandlers.ErrorText.COMMAND_NOT_FOUND;

/**
 * Created by ksg on 6/17/16
 */
public class TimelineHandler extends BaseHandler {

    public TimelineHandler(SpeechCommandDAO speechCommandDAO) {
        super("timeline", speechCommandDAO, getAvailableActions());
    }

    private static Map<String, SpeechCommand> getAvailableActions() {
        // TODO read from DynamoDB
        final Map<String, SpeechCommand> tempMap = Maps.newHashMap();
//        tempMap.put("sleep last", SpeechCommand.TIMELINE);
        tempMap.put("my timeline", SpeechCommand.TIMELINE);
        return tempMap;
    }

    @Override
    public HandlerResult executeCommand(final AnnotatedTranscript annotatedTranscript, final VoiceRequest request) {
        // TODO
        final String text = annotatedTranscript.transcript;

        String command = HandlerResult.EMPTY_COMMAND;
        GenericResult result = GenericResult.fail(COMMAND_NOT_FOUND);

        final Optional<SpeechCommand> optionalCommand = getCommand(text);
        if (optionalCommand.isPresent()) {
            command = optionalCommand.get().getValue();
            result = GenericResult.ok("Your sleep timeline is being computed");
        }

        return new HandlerResult(HandlerType.ALARM, command, result);
    }

    @Override
    public SupichiResponseType responseType() {
        return SupichiResponseType.WATSON;
    }
}