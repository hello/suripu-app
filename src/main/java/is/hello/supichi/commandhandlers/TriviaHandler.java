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


    private final SpeechCommandDAO speechCommandDAO;

    public TriviaHandler(final SpeechCommandDAO speechCommandDAO) {
        super("time_report", speechCommandDAO, getAvailableActions());
        this.speechCommandDAO = speechCommandDAO;
    }

    private static Map<String, SpeechCommand> getAvailableActions() {
        final Map<String, SpeechCommand> tempMap = Maps.newHashMap();
        tempMap.put("the president", SpeechCommand.TRIVIA);
        tempMap.put("hello ceo", SpeechCommand.TRIVIA);
        tempMap.put("hello co", SpeechCommand.TRIVIA);
        tempMap.put("next president", SpeechCommand.TRIVIA);
        tempMap.put("best basketball", SpeechCommand.TRIVIA);
        tempMap.put("favorite retailer", SpeechCommand.TRIVIA);
        return tempMap;
    }


    @Override
    public HandlerResult executeCommand(final AnnotatedTranscript annotatedTranscript, final VoiceRequest request) {
        final String text = annotatedTranscript.transcript.toLowerCase();

        final Optional<SpeechCommand> optionalCommand = getCommand(text);
        String command = HandlerResult.EMPTY_COMMAND;

        String fileMarker = "";
        GenericResult result = GenericResult.fail(COMMAND_NOT_FOUND);
        if (optionalCommand.isPresent()) {
            command = optionalCommand.get().getValue();
            if (text.contains("the president")) {
                fileMarker = "president_obama";
                result = GenericResult.ok("The current president of the United States is Barack Obama.");

            } else if (text.contains("hello ceo") || text.contains("hello co")) {
                fileMarker = "hello_ceo_james";
                result = GenericResult.ok("The current CEO of Hello Inc. is James Proud.");

            } else if (text.contains("next president")) {
                fileMarker = "next_president";
                result = GenericResult.ok("The next president of the United States will either be Hillary Clinton, or Donald Trump.");

            } else if (text.contains("best basketball")) {
                fileMarker = "best_basketball";
                result = GenericResult.ok("The best basketball team in the NBA is the Golden State Warriors.");

            } else if (text.contains("favorite retailer")) {
                fileMarker = "retailer_best_buy";
                result = GenericResult.ok("Hello's favorite retailer is best buy.");
            }
        }
        return HandlerResult.withFileMarker(HandlerType.TRIVIA, command, result, fileMarker);
    }

}
