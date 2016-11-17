package is.hello.supichi.commandhandlers;

import com.google.common.collect.Maps;
import is.hello.supichi.commandhandlers.results.GenericResult;
import is.hello.supichi.db.SpeechCommandDAO;
import is.hello.supichi.models.AnnotatedTranscript;
import is.hello.supichi.models.HandlerResult;
import is.hello.supichi.models.HandlerType;
import is.hello.supichi.models.SpeechCommand;
import is.hello.supichi.models.VoiceRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class WaitHandler extends BaseHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(WaitHandler.class);

    public WaitHandler(final SpeechCommandDAO speechCommandDAO) {
        super("wait", speechCommandDAO, getAvailableActions());
    }

    private static Map<String, SpeechCommand> getAvailableActions() {
        // TODO read from DynamoDB
        final Map<String, SpeechCommand> tempMap = Maps.newHashMap();
        tempMap.put("wait please", SpeechCommand.WAIT);
        return tempMap;
    }


    @Override
    public HandlerResult executeCommand(AnnotatedTranscript annotatedTranscript, VoiceRequest request) {
        LOGGER.info("cmd=wait sense_id={} account_id={} transcript={}", request.senseId, request.accountId, request.transcript);
        try {
            Thread.sleep(15000L);
        } catch (InterruptedException e) {
            LOGGER.error("error=interupted-sleep msg={} sense_id={}", e.getMessage(), request.senseId);
        }
        final GenericResult result = GenericResult.ok("I waited 15 seconds");
        return new HandlerResult(HandlerType.WAIT, SpeechCommand.WAIT.getValue(), result);
    }
}
