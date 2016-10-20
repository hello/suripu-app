package is.hello.supichi.commandhandlers;

import com.google.common.base.Optional;
import com.hello.suripu.core.db.TimeZoneHistoryDAODynamoDB;
import com.hello.suripu.core.models.TimeZoneHistory;
import com.maxmind.geoip2.DatabaseReader;
import is.hello.supichi.db.SpeechCommandDAO;
import is.hello.supichi.models.AnnotatedTranscript;
import is.hello.supichi.models.HandlerResult;
import is.hello.supichi.models.VoiceRequest;
import is.hello.supichi.utils.GeoUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static is.hello.supichi.commandhandlers.results.Outcome.FAIL;
import static is.hello.supichi.commandhandlers.results.Outcome.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.hamcrest.Matchers.is;


/**
 * Created by ksg on 10/17/16
 */
public class TimeHandlerTestIT {
    private final SpeechCommandDAO speechCommandDAO = mock(SpeechCommandDAO.class);
    private final TimeZoneHistoryDAODynamoDB timeZoneHistoryDAODynamoDB = mock(TimeZoneHistoryDAODynamoDB.class);
    private TimeHandler timeHandler;


    private static final String GOOD_IP = "204.28.123.251";
    private static final String BAD_IP = "0.0.0.0";
    private static final String TEXT = "What is the time?";
    private static final Long OK_ACCOUNT_ID = 1L;
    private static final Long BAD_ACCOUNT_ID = 2L;

    @Before
    public void setUp() {
        Optional<DatabaseReader> geoIPDatabase = GeoUtils.geoIPDatabase();

        final DateTime now = DateTime.now(DateTimeZone.UTC);
        final TimeZoneHistory timeZoneHistory = new TimeZoneHistory(now.getMillis(), 0, "America/Los_Angeles");
        doReturn(Optional.of(timeZoneHistory)).when(timeZoneHistoryDAODynamoDB).getCurrentTimeZone(OK_ACCOUNT_ID);
        doReturn(Optional.absent()).when(timeZoneHistoryDAODynamoDB).getCurrentTimeZone(BAD_ACCOUNT_ID);

        timeHandler = new TimeHandler(speechCommandDAO, timeZoneHistoryDAODynamoDB, geoIPDatabase);

    }

    @After
    public void tearDown() {}

    @Test
    public void testTimePass() {
        final AnnotatedTranscript annotatedTranscript = new AnnotatedTranscript.Builder().withTranscript(TEXT).build();
        final VoiceRequest voiceRequest = new VoiceRequest("ABCD", OK_ACCOUNT_ID, TEXT, BAD_IP);

        final HandlerResult result = timeHandler.executeCommand(annotatedTranscript, voiceRequest);
        assertThat(result.outcome(), is(OK));
    }

    @Test
    public void testTimeFail() {
        final AnnotatedTranscript annotatedTranscript = new AnnotatedTranscript.Builder().withTranscript(TEXT).build();
        final VoiceRequest voiceRequest = new VoiceRequest("ABCD", BAD_ACCOUNT_ID, TEXT, BAD_IP);

        final HandlerResult result = timeHandler.executeCommand(annotatedTranscript, voiceRequest);
        assertThat(result.outcome(), is(FAIL));
    }

    @Test
    public void testTimePassWithIP() {
        final AnnotatedTranscript annotatedTranscript = new AnnotatedTranscript.Builder().withTranscript(TEXT).build();
        final VoiceRequest voiceRequest = new VoiceRequest("ABCD", BAD_ACCOUNT_ID, TEXT, GOOD_IP);

        final HandlerResult result = timeHandler.executeCommand(annotatedTranscript, voiceRequest);
        assertThat(result.outcome(), is(OK));
    }

}