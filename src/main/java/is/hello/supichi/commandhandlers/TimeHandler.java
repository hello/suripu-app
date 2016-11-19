package is.hello.supichi.commandhandlers;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.hello.suripu.core.db.TimeZoneHistoryDAODynamoDB;
import com.hello.suripu.core.models.TimeZoneHistory;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
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

import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;

import static is.hello.supichi.commandhandlers.ErrorText.COMMAND_NOT_FOUND;
import static is.hello.supichi.commandhandlers.ErrorText.NO_TIMEZONE;


/**
 * Created by ksg on 6/17/16
 */
public class TimeHandler extends BaseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimeHandler.class);

    private static final String TIMEZONE_ERROR_TEXT = "Sorry, I'm not able to get the time. Please set your timezone in the mobile app.";
    private static final String TIME_ERROR_TEXT = "Sorry, I'm not able to determine the time right now. Please try again later.";

    private static final String TIME_RESPONSE_TEXT_FORMATTER = "The time is %s.";

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
        final String text = annotatedTranscript.lowercaseTranscript();

        final Optional<SpeechCommand> optionalCommand = getCommand(annotatedTranscript); // TODO: ensure that only valid commands are returned

        if (optionalCommand.isPresent()) {
            final String command = optionalCommand.get().getValue();
            Optional<String> optionalTimeZoneId = getTimeZone(request.accountId, request.ipAddress);

            if (!optionalTimeZoneId.isPresent()) {
                return new HandlerResult(HandlerType.TIME_REPORT, command, GenericResult.failWithResponse(NO_TIMEZONE, TIMEZONE_ERROR_TEXT));
            }

            final DateTimeZone userTimeZone = DateTimeZone.forID(optionalTimeZoneId.get());
            final DateTime localNow = DateTime.now(DateTimeZone.UTC).withZone(userTimeZone);

            final String currentTime = localNow.toString("h:mm a");
            LOGGER.debug("action=get-current-time local_now={} string={} time_zone={} account_id={}",
                    localNow.toString(), currentTime, userTimeZone.toString(), request.accountId);

            return new HandlerResult(HandlerType.TIME_REPORT, command, GenericResult.ok(String.format(TIME_RESPONSE_TEXT_FORMATTER, currentTime)));
        }

        return new HandlerResult(HandlerType.TIME_REPORT, HandlerResult.EMPTY_COMMAND, GenericResult.failWithResponse(COMMAND_NOT_FOUND, TIME_ERROR_TEXT));
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

    @Override
    public SupichiResponseType responseType() {
        return SupichiResponseType.WATSON;
    }

}
