package is.hello.supichi.commandhandlers;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import is.hello.supichi.commandhandlers.results.GenericResult;
import is.hello.supichi.db.SpeechCommandDAO;
import is.hello.supichi.models.AnnotatedTranscript;
import is.hello.supichi.models.HandlerResult;
import is.hello.supichi.models.HandlerType;
import is.hello.supichi.models.SpeechCommand;
import is.hello.supichi.models.VoiceRequest;
import is.hello.supichi.response.SupichiResponseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static is.hello.supichi.commandhandlers.ErrorText.COMMAND_NOT_FOUND;


/**
 * Created by ksg on 6/17/16
 */
public class TriviaHandler extends BaseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TriviaHandler.class);

    private static final String DEFAULT_SENSOR_UNIT = "f";
    private static final Float NO_SOUND_FILL_VALUE_DB = (float) 35; // Replace with this value when Sense isn't capturing audio

    private static final String NO_COMMAND_RESPONSE_TEXT = "Sorry, there was a problem. Please try again later.";

    private final SpeechCommandDAO speechCommandDAO;

    public TriviaHandler(final SpeechCommandDAO speechCommandDAO) {
        super("trivia", speechCommandDAO, getAvailableActions());
        this.speechCommandDAO = speechCommandDAO;
    }

    private static Map<String, SpeechCommand> getAvailableActions() {
        final Map<String, SpeechCommand> tempMap = Maps.newHashMap();
        tempMap.put("best basketball", SpeechCommand.TRIVIA);
        tempMap.put("best nba", SpeechCommand.TRIVIA);
        tempMap.put("best team in nba", SpeechCommand.TRIVIA);
        return tempMap;
    }


    @Override
    public HandlerResult executeCommand(final AnnotatedTranscript annotatedTranscript, final VoiceRequest request) {
        final String text = annotatedTranscript.transcript.toLowerCase();

        final Optional<SpeechCommand> optionalCommand = getCommand(text);
        String command = HandlerResult.EMPTY_COMMAND;

        GenericResult result = GenericResult.failWithResponse(COMMAND_NOT_FOUND, NO_COMMAND_RESPONSE_TEXT);
        if (optionalCommand.isPresent()) {
            command = optionalCommand.get().getValue();
            if (text.contains("best basketball") || text.contains("nba")) {
                result = GenericResult.ok("The best basketball team in the NBA is the Golden State Warriors.");

            }
        }
        return new HandlerResult(HandlerType.TRIVIA, command, result);
    }

    @Override
    public SupichiResponseType responseType() {
        return SupichiResponseType.WATSON;
    }

}
