package is.hello.supichi.commandhandlers;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.hello.suripu.app.sensors.SensorResponse;
import com.hello.suripu.app.sensors.SensorUnit;
import com.hello.suripu.app.sensors.SensorView;
import com.hello.suripu.app.sensors.SensorViewLogic;
import com.hello.suripu.core.models.Sensor;
import com.hello.suripu.core.preferences.AccountPreferencesDAO;
import com.hello.suripu.core.preferences.PreferenceName;
import com.hello.suripu.core.roomstate.Condition;
import is.hello.supichi.commandhandlers.results.GenericResult;
import is.hello.supichi.commandhandlers.results.RoomConditionResult;
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
import java.util.stream.Collectors;

import static is.hello.supichi.commandhandlers.ErrorText.COMMAND_NOT_FOUND;
import static is.hello.supichi.commandhandlers.ErrorText.ERROR_DATA_TOO_OLD;
import static is.hello.supichi.commandhandlers.ErrorText.ERROR_INVALID_SENSOR;
import static is.hello.supichi.commandhandlers.ErrorText.ERROR_NO_DATA;


/**
 * Created by ksg on 6/17/16
 */
public class RoomConditionsHandler extends BaseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomConditionsHandler.class);

    private static final boolean DEFAULT_USE_FAHRENHEIT = false; // check is for use-Celsius
    private static final String ROOM_CONDITION_PATTERN = "(bed)?room('s)? condition";
    private static final String TEMPERATURE_PATTERN = "what('s)?.+\\s(temperature)";
    private static final String TEMPERATURE_PATTERN_HOW = "how('s)?.+\\s(temperature)";
    private static final String HUMIDITY_PATTERN = "what('s)?.*\\s(humidity)";
    private static final String HUMIDITY_PATTERN_HOW = "how('s)?.*\\s(humidity)";

    private static final String NO_DATA_ERROR_RESPONSE_TEXT = "Sorry, I wasn't able to access your %s data right now. Please try again later";
    private static final String ROOM_CONDITION_UNAVAILABLE_RESPONSE_TEXT = "Room conditions are currently unavailable. Please try again later.";

    // response text formatting
    private static final String RESPONSE_FORMAT_SECOND_CHOICE = "It's currently %s %s.";
    private static ImmutableMap<SpeechCommand, String> sensorResponseFormat;
    static {
        final Map<SpeechCommand, String> temp = Maps.newHashMap();
        temp.put(SpeechCommand.ROOM_TEMPERATURE, "The temperature in your room is %s %s.");
        temp.put(SpeechCommand.ROOM_HUMIDITY, "The humidity in your room is %s %s.");
        temp.put(SpeechCommand.ROOM_LIGHT,"The light level in your room is %s %s.");
        temp.put(SpeechCommand.ROOM_SOUND,"The sound in your room is %s %s");
        temp.put(SpeechCommand.PARTICULATES,"The air quality in your room is %s %s");
        sensorResponseFormat = ImmutableMap.copyOf(temp);
    }

    private final SpeechCommandDAO speechCommandDAO;
    private final AccountPreferencesDAO accountPreferencesDAO;
    private final SensorViewLogic sensorViewLogic;

    private static ImmutableMap<SpeechCommand, Sensor> commandSensorMap;
    static {
        final Map<SpeechCommand, Sensor> temp = Maps.newHashMap();
        temp.put(SpeechCommand.ROOM_TEMPERATURE, Sensor.TEMPERATURE);
        temp.put(SpeechCommand.ROOM_HUMIDITY, Sensor.HUMIDITY);
        temp.put(SpeechCommand.ROOM_LIGHT, Sensor.LIGHT);
        temp.put(SpeechCommand.ROOM_SOUND, Sensor.SOUND);
        temp.put(SpeechCommand.PARTICULATES, Sensor.PARTICULATES);
        commandSensorMap = ImmutableMap.copyOf(temp);
    }

    private static ImmutableMap<Condition, String> roomConditionResponseText;
    static {
        final Map<Condition, String> text = Maps.newHashMap();
        text.put(Condition.IDEAL, "Room conditions are just right.");
        text.put(Condition.WARNING, "Room conditions are less than ideal.");
        text.put(Condition.ALERT, "Room conditions might prevent restful sleep.");
        roomConditionResponseText = ImmutableMap.copyOf(text);
    }

    public RoomConditionsHandler(final SpeechCommandDAO speechCommandDAO,
                                 final AccountPreferencesDAO accountPreferencesDAO,
                                 final SensorViewLogic sensorViewLogic) {
        super("room_condition", speechCommandDAO, getAvailableActions());
        this.speechCommandDAO = speechCommandDAO;
        this.accountPreferencesDAO = accountPreferencesDAO;
        this.sensorViewLogic = sensorViewLogic;
    }

    private static Map<String, SpeechCommand> getAvailableActions() {
        final Map<String, SpeechCommand> tempMap = Maps.newHashMap();
        tempMap.put(TEMPERATURE_PATTERN, SpeechCommand.ROOM_TEMPERATURE);
        tempMap.put(TEMPERATURE_PATTERN_HOW, SpeechCommand.ROOM_TEMPERATURE);
        tempMap.put("what temperature", SpeechCommand.ROOM_TEMPERATURE);
        tempMap.put(HUMIDITY_PATTERN, SpeechCommand.ROOM_HUMIDITY);
        tempMap.put(HUMIDITY_PATTERN_HOW, SpeechCommand.ROOM_HUMIDITY);
        tempMap.put("what humidity", SpeechCommand.ROOM_HUMIDITY);
        tempMap.put("light level", SpeechCommand.ROOM_LIGHT);
        tempMap.put("how bright", SpeechCommand.ROOM_LIGHT);
        tempMap.put("sound level", SpeechCommand.ROOM_SOUND);
        tempMap.put("noise level", SpeechCommand.ROOM_SOUND);
        tempMap.put("how noisy", SpeechCommand.ROOM_SOUND);
        tempMap.put("air quality", SpeechCommand.PARTICULATES);
        tempMap.put(ROOM_CONDITION_PATTERN, SpeechCommand.ROOM_CONDITION);
        return tempMap;
    }


    @Override
    public HandlerResult executeCommand(final AnnotatedTranscript annotatedTranscript, final VoiceRequest request) {

        final Optional<SpeechCommand> optionalCommand = getCommand(annotatedTranscript); // TODO: ensure that only valid commands are returned

        if (optionalCommand.isPresent()) {
            return getCurrentRoomConditions(request.accountId, optionalCommand.get());
        }
        return new HandlerResult(HandlerType.ROOM_CONDITIONS, HandlerResult.EMPTY_COMMAND, GenericResult.fail(COMMAND_NOT_FOUND));
    }

    @Override
    public Integer matchAnnotations(final AnnotatedTranscript annotatedTranscript) {
        // TODO: add SensorAnnotation
        return NO_ANNOTATION_SCORE;
    }

    private HandlerResult getCurrentRoomConditions(final Long accountId, final SpeechCommand command) {

        final String sensorName = getSensorName(command);
        if (sensorName.isEmpty()) {
            return new HandlerResult(HandlerType.ROOM_CONDITIONS, command.getValue(), GenericResult.fail(ERROR_INVALID_SENSOR));
        }

        // get current sensor data
        final DateTime asOfUTC = DateTime.now(DateTimeZone.UTC);
        final SensorResponse sensorResponse = sensorViewLogic.list(accountId, asOfUTC);

        switch (sensorResponse.status()) {
            case NO_SENSE:
                LOGGER.error("error=no-sensor-data reason=no-paired-sense account_id={}", accountId);
                return new HandlerResult(HandlerType.ROOM_CONDITIONS, command.getValue(),
                        GenericResult.failWithResponse(ERROR_NO_DATA, String.format(NO_DATA_ERROR_RESPONSE_TEXT, sensorName)));

            case WAITING_FOR_DATA:
                LOGGER.error("error=no-sensor-data reason=data-too-old account_id={} as_of_utc_now={}", accountId, asOfUTC);
                return new HandlerResult(HandlerType.ROOM_CONDITIONS, command.getValue(),
                        GenericResult.failWithResponse(ERROR_DATA_TOO_OLD, String.format(NO_DATA_ERROR_RESPONSE_TEXT, sensorName)));

            case OK:
                return okSensorResult(accountId, command, sensorResponse);
        }

        // uh-oh, something wrong
        LOGGER.error("error=fail-to-get-sensor-data account_id={} as_of_utc_now={}", accountId, asOfUTC);
        return new HandlerResult(HandlerType.ROOM_CONDITIONS, command.getValue(),
                GenericResult.failWithResponse(ERROR_NO_DATA, String.format(NO_DATA_ERROR_RESPONSE_TEXT, sensorName)));
    }


    private HandlerResult okSensorResult(final Long accountId,
                                         final SpeechCommand command,
                                         final SensorResponse sensorResponse) {

        final Map<Sensor, SensorView> sensorViewMap = sensorResponse.sensors().stream()
                .collect(Collectors.toMap(SensorView::sensor, item -> item));

        final String sensorName = getSensorName(command);

        // current room-state command
        if (command.equals(SpeechCommand.ROOM_CONDITION)) {
            final Condition condition = sensorResponse.condition();

            final RoomConditionResult roomResult = new RoomConditionResult(sensorName, condition.toString(), "", condition);
            final String responseText = roomConditionResponseText.getOrDefault(condition, ROOM_CONDITION_UNAVAILABLE_RESPONSE_TEXT);
            return HandlerResult.withRoomResult(HandlerType.ROOM_CONDITIONS, command.getValue(), GenericResult.ok(responseText), roomResult);
        }

        // all other sensors command
        final Sensor sensor = commandSensorMap.get(command);
        if (!sensorViewMap.containsKey(sensor)) {
            // most likely due to missing dust calibration
            LOGGER.error("error=missing-sensor-data sensor={} account_id={}", sensor.toString(), accountId);
            return new HandlerResult(HandlerType.ROOM_CONDITIONS, command.getValue(), GenericResult.fail(ERROR_NO_DATA));
        }

        final SensorView sensorView = sensorViewMap.get(commandSensorMap.get(command));

        final String sensorValue;
        final String sensorUnit;

        if (command.equals(SpeechCommand.ROOM_TEMPERATURE)) {
            // get unit preference, default to use Fahrenheit
            final float temperatureValue = Math.round(sensorView.value());

            final Map<PreferenceName, Boolean> preferences = accountPreferencesDAO.get(accountId);
            final Boolean useCelsius = preferences.getOrDefault(PreferenceName.TEMP_CELSIUS, DEFAULT_USE_FAHRENHEIT);

            if (useCelsius) {
                sensorUnit = SensorUnit.CELSIUS.value();
                sensorValue = String.valueOf(Math.round(temperatureValue));
            } else {
                // cause we're special
                sensorUnit = SensorUnit.FAHRENHEIT.value();
                sensorValue = String.valueOf(celsiusToFahrenheit(temperatureValue));
            }
        } else {
            sensorValue = String.valueOf(Math.round(sensorView.value()));
            sensorUnit = sensorView.unit().value();
        }

        LOGGER.debug("action=get-room-condition-ok command={} value={}, unit={}", command.toString(), sensorValue, sensorUnit);

        final RoomConditionResult roomResult = new RoomConditionResult(sensorName, sensorValue, sensorUnit, Condition.UNKNOWN);
        final String responseText = responseText(command, sensorValue, sensorUnit);

        return HandlerResult.withRoomResult(HandlerType.ROOM_CONDITIONS, command.getValue(), GenericResult.ok(responseText), roomResult);
    }

    private String responseText(final SpeechCommand command, final String sensorValue, final String sensorUnit) {
        final boolean chooseOne = (DateTime.now(DateTimeZone.UTC).getMillis() % 2) == 0;
        if (chooseOne) {
            final String responseTextFormat = sensorResponseFormat.getOrDefault(command, RESPONSE_FORMAT_SECOND_CHOICE);
            return String.format(responseTextFormat, sensorValue, sensorUnit);
        }

        return String.format(RESPONSE_FORMAT_SECOND_CHOICE, sensorValue, sensorUnit);
    }


    private static int celsiusToFahrenheit(final double value) {
        return (int) Math.round((value * 9.0) / 5.0) + 32;
    }

    private String getSensorName(final SpeechCommand command) {
        switch (command) {
            case ROOM_TEMPERATURE:
                return Sensor.TEMPERATURE.toString();
            case ROOM_HUMIDITY:
                return Sensor.HUMIDITY.toString();
            case ROOM_LIGHT:
                return Sensor.LIGHT.toString();
            case ROOM_SOUND:
                return Sensor.SOUND.toString();
            case PARTICULATES:
                return Sensor.PARTICULATES.toString();
            case ROOM_CONDITION:
                return SpeechCommand.ROOM_CONDITION.getValue();
        }
        return "";
    }

    @Override
    public SupichiResponseType responseType() {
        return SupichiResponseType.WATSON;
    }

}
