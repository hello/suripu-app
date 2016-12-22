package is.hello.supichi.commandhandlers;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.hello.suripu.core.db.AccountDAO;
import com.hello.suripu.core.models.Account;
import is.hello.supichi.commandhandlers.results.GenericResult;
import is.hello.supichi.db.SpeechCommandDAO;
import is.hello.supichi.models.AnnotatedTranscript;
import is.hello.supichi.models.HandlerResult;
import is.hello.supichi.models.HandlerType;
import is.hello.supichi.models.SpeechCommand;
import is.hello.supichi.models.VoiceRequest;
import is.hello.supichi.response.SupichiResponseType;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.TimeZone;

import static is.hello.supichi.commandhandlers.ErrorText.COMMAND_NOT_FOUND;


/**
 * Created by ksg on 6/17/16
 */
public class TriviaHandler extends BaseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TriviaHandler.class);

    private static final String NO_COMMAND_RESPONSE_TEXT = "Sorry, there was a problem. Please try again later.";

    private final SpeechCommandDAO speechCommandDAO;
    private final boolean isDebug;
    private final AccountDAO accountDAO;

    public TriviaHandler(final SpeechCommandDAO speechCommandDAO, final AccountDAO accountDAO, final boolean isDebug) {
        super("trivia", speechCommandDAO, getAvailableActions());
        this.speechCommandDAO = speechCommandDAO;
        this.accountDAO = accountDAO;
        this.isDebug = isDebug;
    }

    private static Map<String, SpeechCommand> getAvailableActions() {
        final Map<String, SpeechCommand> tempMap = Maps.newHashMap();
        tempMap.put("best basketball", SpeechCommand.TRIVIA);
        tempMap.put("best nba", SpeechCommand.TRIVIA);
        tempMap.put("best baseball", SpeechCommand.TRIVIA);
        tempMap.put("best team in nba", SpeechCommand.TRIVIA);
        tempMap.put("happy holidays", SpeechCommand.TRIVIA);
        tempMap.put("good morning", SpeechCommand.TRIVIA);
        tempMap.put("good afternoon", SpeechCommand.TRIVIA);
        tempMap.put("good night", SpeechCommand.TRIVIA);
        tempMap.put("goodnight", SpeechCommand.TRIVIA);
        tempMap.put("your father", SpeechCommand.TRIVIA);
        tempMap.put("who am i", SpeechCommand.TRIVIA);
        tempMap.put("send message", SpeechCommand.TRIVIA);
        return tempMap;
    }


    @Override
    public HandlerResult executeCommand(final AnnotatedTranscript annotatedTranscript, final VoiceRequest request) {
        final String text = annotatedTranscript.lowercaseTranscript();

        final Optional<SpeechCommand> optionalCommand = getCommand(annotatedTranscript);
        String command = HandlerResult.EMPTY_COMMAND;
        Optional<Account> account = accountDAO.getById(request.accountId);

        GenericResult result = GenericResult.failWithResponse(COMMAND_NOT_FOUND, NO_COMMAND_RESPONSE_TEXT);
        if (optionalCommand.isPresent()) {
            // 3E4C72A31C78C0F6 is Kyrk
            final boolean hasBaseball = request.senseId.equals("3E4C72A31C78C0F6") || isDebug;
            command = optionalCommand.get().getValue();
            if (text.contains("best basketball") || text.contains("nba")) {
                result = GenericResult.ok("The best basketball team in the NBA is the Golden State Warriors.");

            } else if(text.contains("best baseball") && hasBaseball) {
                result = GenericResult.ok("The Chicago Cubs are surprisingly the best baseball team in the MLB.");
            } else if (text.contains("happy holidays")) {
                result = GenericResult.withMP3("happy holidays", "christmas-short.mp3");
            } else if(text.contains("good night") || text.contains("goodnight")) {
                result = GenericResult.ok("Good night!");
            } else if (text.contains("your father")) {
                result = GenericResult.withMP3("your father is", "vader.mp3");
            } else if (text.contains("who am i") && account.isPresent()) {
                result = GenericResult.ok(String.format("Hello %s!", account.get().firstname));
            }

            if (isDebug) {
                if(text.contains("good morning") || text.contains("good afternoon")) {
                    result = greetings(annotatedTranscript.timeZoneOptional, DateTime.now(DateTimeZone.UTC));
                }
            }
        }
        return new HandlerResult(HandlerType.TRIVIA, command, result);
    }


    static GenericResult greetings(final Optional<TimeZone> timeZone, final DateTime local) {
        String tempText = "Hello.";
        if(timeZone.isPresent()) {

            final DateTime nowLocal = new DateTime(local.getMillis(), DateTimeZone.forID(timeZone.get().getID()));
            final Integer hourOfDay = nowLocal.getHourOfDay();
            if(hourOfDay >= 2 && hourOfDay < 12) {
                tempText = "Good morning";
            } else if(hourOfDay >= 12 && hourOfDay < 18) {
                tempText = "Good afternoon";
            }
        }

        return GenericResult.ok(tempText);
    }

    @Override
    public SupichiResponseType responseType() {
        return SupichiResponseType.WATSON;
    }

}
