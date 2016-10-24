package is.hello.supichi.commandhandlers;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.hello.suripu.core.db.AccountLocationDAO;
import com.hello.suripu.core.models.AccountLocation;
import is.hello.gaibu.weather.interfaces.WeatherReport;
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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

public class WeatherHandler extends BaseHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(WeatherHandler.class);

    private static Map<String, SpeechCommand> commands;
    private final AccountLocationDAO accountLocationDAO;
    private final WeatherReport report;
    static {
        final Map<String, SpeechCommand> aMap = new HashMap<>();
        aMap.put("the weather", SpeechCommand.WEATHER);
        commands = ImmutableMap.copyOf(aMap);
    }


    private WeatherHandler(final SpeechCommandDAO speechCommandDAO, final WeatherReport report, final AccountLocationDAO accountLocationDAO) {
        super("weather", speechCommandDAO, commands);
        this.accountLocationDAO = accountLocationDAO;
        this.report = report;
    }


    public static WeatherHandler create(final SpeechCommandDAO speechCommandDAO, final WeatherReport report, final AccountLocationDAO accountLocationDAO) {
        return new WeatherHandler(speechCommandDAO, report, accountLocationDAO);
    }

    @Override
    public HandlerResult executeCommand(final AnnotatedTranscript annotatedTranscript, final VoiceRequest request) {
        final String text = annotatedTranscript.transcript;

        final Optional<AccountLocation> accountLocationOptional = accountLocationDAO.getLastLocationByAccountId(request.accountId);
        final Map<String,String> params = Maps.newHashMap();

        // 438 shotwell latitude: 37.761185, longitude: -122.416369
        LOGGER.info("action=weather-handler-execute account_id={} sense_id={} ip_address={}", request.accountId, request.senseId, request.ipAddress);

        GenericResult result;
        if(accountLocationOptional.isPresent()) {
            LOGGER.info("action=get-location account_id={} result=found", request.accountId);
            final Double latitude = accountLocationOptional.isPresent() ? accountLocationOptional.get().latitude : 37.761185;
            final Double longitude = accountLocationOptional.isPresent() ? accountLocationOptional.get().longitude : -122.416369;

            LOGGER.info("action=get-forecast account_id={} latitude={} longitude={}", request.accountId, latitude, longitude);

            final String summary = report.get(latitude, longitude, request.accountId, request.senseId);
            LOGGER.info("action=get-forecast account_id={} result={}", request.accountId, summary);
            result = GenericResult.ok(summary);

        } else {
            try {
                final String summary = report.get(request.accountId, request.senseId, InetAddress.getByName(request.ipAddress));
                LOGGER.info("action=get-forecast account_id={} result={}", request.accountId, summary);
                result = GenericResult.ok(summary);
            } catch (UnknownHostException e) {
                LOGGER.info("error=get-time-geoip-fail account_id={} msg={}", request.accountId, e.getMessage());
                result = GenericResult.failWithResponse("weather not available", "Server error.");
            }
        }

        return new HandlerResult(HandlerType.WEATHER, SpeechCommand.WEATHER.getValue(), result);
    }

    @Override
    public Integer matchAnnotations(final AnnotatedTranscript annotatedTranscript) {
        // TODO: Location
        return NO_ANNOTATION_SCORE;
    }

    @Override
    public SupichiResponseType responseType() {
        return SupichiResponseType.WATSON;
    }
}
