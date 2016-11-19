package is.hello.supichi.commandhandlers;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import is.hello.supichi.db.SpeechCommandDAO;
import is.hello.supichi.models.AnnotatedTranscript;
import is.hello.supichi.models.HandlerResult;
import is.hello.supichi.models.SpeechCommand;
import is.hello.supichi.models.VoiceRequest;
import is.hello.supichi.response.SupichiResponseType;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by ksg on 6/20/16
 */
public abstract class BaseHandler {
    public static final int NO_ANNOTATION_SCORE = 0;

    private final String handlerName;
    private final ImmutableMap<String, SpeechCommand> commandMap;
    private final SpeechCommandDAO speechCommandDAO;

    BaseHandler(final String handlerName, final SpeechCommandDAO speechCommandDAO, final Map<String, SpeechCommand> commandMap) {
        this.handlerName = handlerName;
        this.speechCommandDAO = speechCommandDAO;
        this.commandMap = ImmutableMap.copyOf(commandMap);
    }

    public Set<String> getRelevantCommands() {
        return this.commandMap.keySet();
    }

    public Optional<SpeechCommand> getCommand(final AnnotatedTranscript transcript) {
        final String text = transcript.lowercaseTranscript();
        if (commandMap.containsKey(text)) {
            return Optional.of(commandMap.get(text));
        }
        for(final String key:commandMap.keySet()) {
            if(text.contains(key)) {
                return Optional.of(commandMap.get(key));
            }
            //Check if there is a pattern match when treating the commandMap key as a regex pattern
            final Pattern r = Pattern.compile(key);
            Matcher m = r.matcher(text);
            if(m.find()) {
                return Optional.of(commandMap.get(key));
            }
        }
        return Optional.absent();
    }

    public abstract HandlerResult executeCommand(final AnnotatedTranscript annotatedTranscript, final VoiceRequest request);

    public Integer matchAnnotations(final AnnotatedTranscript annotatedTranscript) {
        return NO_ANNOTATION_SCORE;
    }

    public SupichiResponseType responseType() {
        return SupichiResponseType.STATIC;
    };
}
