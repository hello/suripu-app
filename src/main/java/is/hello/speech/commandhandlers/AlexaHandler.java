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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static is.hello.speech.commandhandlers.ErrorText.COMMAND_NOT_FOUND;


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
        // TODO read from DynamoDB
        final Map<String, SpeechCommand> tempMap = Maps.newHashMap();
        tempMap.put("ask Alexa", SpeechCommand.ALEXA);
        return tempMap;
    }


    @Override
    public HandlerResult executeCommand(final AnnotatedTranscript annotatedTranscript, final VoiceRequest request) {
        final String text = annotatedTranscript.transcript;

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
