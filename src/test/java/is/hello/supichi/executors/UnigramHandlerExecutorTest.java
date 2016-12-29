package is.hello.supichi.executors;

import com.hello.suripu.core.db.AccountDAO;
import is.hello.supichi.commandhandlers.TriviaHandler;
import is.hello.supichi.db.SpeechCommandDAO;
import is.hello.supichi.models.HandlerResult;
import is.hello.supichi.models.HandlerType;
import is.hello.supichi.models.VoiceRequest;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class UnigramHandlerExecutorTest {

    @Test
    public void TestHandleEmptyHandler() {

        final UnigramHandlerExecutor executor = new UnigramHandlerExecutor();
        final HandlerResult result = executor.handle(new VoiceRequest("123456789", 99L, "whatever", ""));
        assertEquals(result.handlerType, HandlerType.NONE);
    }

    @Test
    public void TestHandleSingleHandler() {

        final SpeechCommandDAO speechCommandDAO = mock(SpeechCommandDAO.class);
        final AccountDAO accountDAO = mock(AccountDAO.class);
        final TriviaHandler handler = new TriviaHandler(speechCommandDAO, accountDAO, true);
        final HandlerExecutor executor = new UnigramHandlerExecutor()
                .register(HandlerType.TRIVIA, handler);

        final HandlerResult correctResult = executor.handle(new VoiceRequest("123456789", 99L, "best basketball", ""));
        assertEquals(correctResult.handlerType, HandlerType.TRIVIA);

        final HandlerResult result = executor.handle(new VoiceRequest("123456789", 99L, "whatever", ""));
        assertEquals(result.handlerType, HandlerType.NONE);
    }

}
