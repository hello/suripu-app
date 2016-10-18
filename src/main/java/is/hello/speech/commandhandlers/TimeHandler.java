package is.hello.speech.commandhandlers;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.hello.suripu.core.db.TimeZoneHistoryDAODynamoDB;
import com.hello.suripu.core.models.TimeZoneHistory;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import is.hello.speech.db.SpeechCommandDAO;
import is.hello.speech.commandhandlers.results.GenericResult;
import is.hello.speech.models.AnnotatedTranscript;
import is.hello.speech.models.HandlerResult;
import is.hello.speech.models.HandlerType;
import is.hello.speech.models.SpeechCommand;
import is.hello.speech.models.VoiceRequest;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;

import static is.hello.speech.commandhandlers.ErrorText.COMMAND_NOT_FOUND;
import static is.hello.speech.commandhandlers.ErrorText.NO_TIMEZONE;


/**
 * Created by ksg on 6/17/16
 */
public class TimeHandler extends BaseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimeHandler.class);


    private final SpeechCommandDAO speechCommandDAO;
    private final TimeZoneHistoryDAODynamoDB timeZoneHistoryDAODynamoDB;
    private final Optional<DatabaseReader> geoIPDatabase;

    public TimeHandler(final SpeechCommandDAO speechCommandDAO,
                       final TimeZoneHistoryDAODynamoDB timeZoneHistoryDAODynamoDB,
                       final Optional<DatabaseReader> geoIPDatabase) {
        super("time_report", speechCommandDAO, getAvailableActions());
        this.speechCommandDAO = speechCommandDAO;
        this.timeZoneHistoryDAODynamoDB = timeZoneHistoryDAODynamoDB;
        this.geoIPDatabase = geoIPDatabase;
    }

    private static Map<String, SpeechCommand> getAvailableActions() {
        // TODO read from DynamoDB
        final Map<String, SpeechCommand> tempMap = Maps.newHashMap();
        tempMap.put("the time", SpeechCommand.TIME_REPORT);
        tempMap.put("what time", SpeechCommand.TIME_REPORT);
        return tempMap;
    }


    @Override
    public HandlerResult executeCommand(final AnnotatedTranscript annotatedTranscript, final VoiceRequest request) {
        final String text = annotatedTranscript.transcript;

        final Optional<SpeechCommand> optionalCommand = getCommand(text); // TODO: ensure that only valid commands are returned

        if (optionalCommand.isPresent()) {
            final String command = optionalCommand.get().getValue();
            Optional<String> optionalTimeZoneId = getTimeZone(request.accountId, request.ipAddress);

            if (!optionalTimeZoneId.isPresent()) {
                return new HandlerResult(HandlerType.TIME_REPORT, command, GenericResult.fail(NO_TIMEZONE));
            }

            final DateTimeZone userTimeZone = DateTimeZone.forID(optionalTimeZoneId.get());
            final DateTime localNow = DateTime.now(DateTimeZone.UTC).withZone(userTimeZone);

            final String currentTime = localNow.toString("HH_mm");
            LOGGER.debug("action=get-current-time local_now={} string={} time_zone={} account_id={}",
                    localNow.toString(), currentTime, userTimeZone.toString(), request.accountId);

            return new HandlerResult(HandlerType.TIME_REPORT, command, GenericResult.ok(currentTime));
        }

        return new HandlerResult(HandlerType.TIME_REPORT, HandlerResult.EMPTY_COMMAND, GenericResult.fail(COMMAND_NOT_FOUND));
    }

    @Override
    public Integer matchAnnotations(final AnnotatedTranscript annotatedTranscript) {
        // TODO: add Location
        return NO_ANNOTATION_SCORE;
    }

    private Optional<String> getTimeZone(final Long accountId, final String ipAddress) {
        final Optional<TimeZoneHistory> tzHistory = this.timeZoneHistoryDAODynamoDB.getCurrentTimeZone(accountId);
        if (tzHistory.isPresent()) {
            return Optional.of(tzHistory.get().timeZoneId);
        }

        if (geoIPDatabase.isPresent()) {
            try {
                final CityResponse city = geoIPDatabase.get().city(InetAddress.getByName(ipAddress));
                return Optional.of(city.getLocation().getTimeZone());
            } catch (GeoIp2Exception | IOException e) {
                LOGGER.info("error=get-timezone-via-geoip-fail account_id={} msg={}", accountId, e.getMessage());
            }
        }
        return Optional.absent();
    }
}
