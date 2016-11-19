package is.hello.supichi.commandhandlers;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import is.hello.supichi.db.SpeechCommandDAO;
import is.hello.supichi.commandhandlers.results.GenericResult;
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
public class AlexaHandler extends BaseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlexaHandler.class);

    private static final String DEFAULT_SENSOR_UNIT = "f";
    private static final Float NO_SOUND_FILL_VALUE_DB = (float) 35; // Replace with this value when Sense isn't capturing audio


    private final SpeechCommandDAO speechCommandDAO;

    public AlexaHandler(final SpeechCommandDAO speechCommandDAO) {
        super("time_report", speechCommandDAO, getAvailableActions());
        this.speechCommandDAO = speechCommandDAO;
    }

    private static Map<String, SpeechCommand> getAvailableActions() {
        final Map<String, SpeechCommand> tempMap = Maps.newHashMap();
        tempMap.put("ask Alexa", SpeechCommand.ALEXA);
        return tempMap;
    }


    @Override
    public HandlerResult executeCommand(final AnnotatedTranscript annotatedTranscript, final VoiceRequest request) {
        final String text = annotatedTranscript.lowercaseTranscript();

        final Optional<SpeechCommand> optionalCommand = getCommand(text); // TODO: ensure that only valid commands are returned
        if (optionalCommand.isPresent()) {
            if (text.contains("ask Alexa")) {
                final String truncated = annotatedTranscript.transcript.replace("ask Alexa", "");
                return new HandlerResult(HandlerType.ALEXA, optionalCommand.get().getValue(), GenericResult.ok("Alexa!,  " + truncated));
            }
        }

        return new HandlerResult(HandlerType.ALEXA, HandlerResult.EMPTY_COMMAND, GenericResult.fail(COMMAND_NOT_FOUND));
    }

    @Override
    public SupichiResponseType responseType() {
        return SupichiResponseType.WATSON;
    }

}
