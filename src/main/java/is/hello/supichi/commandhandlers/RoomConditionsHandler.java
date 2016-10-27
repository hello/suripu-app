package is.hello.supichi.commandhandlers;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.hello.suripu.app.sensors.SensorResponse;
import com.hello.suripu.app.sensors.SensorUnit;
import com.hello.suripu.app.sensors.SensorView;
import com.hello.suripu.app.sensors.SensorViewLogic;
import com.hello.suripu.core.db.CalibrationDAO;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.db.DeviceDataDAODynamoDB;
import com.hello.suripu.core.db.colors.SenseColorDAO;
import com.hello.suripu.core.models.Calibration;
import com.hello.suripu.core.models.Device;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.models.DeviceData;
import com.hello.suripu.core.models.Sensor;
import com.hello.suripu.core.roomstate.Condition;
import com.hello.suripu.core.roomstate.CurrentRoomState;
import com.hello.suripu.core.util.RoomConditionUtil;
import is.hello.supichi.commandhandlers.results.GenericResult;
import is.hello.supichi.commandhandlers.results.RoomConditionResult;
import is.hello.supichi.db.SpeechCommandDAO;
import is.hello.supichi.models.AnnotatedTranscript;
import is.hello.supichi.models.HandlerResult;
import is.hello.supichi.models.HandlerType;
import is.hello.supichi.models.SpeechCommand;
import is.hello.supichi.models.VoiceRequest;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.stream.Collectors;

import static is.hello.supichi.commandhandlers.ErrorText.COMMAND_NOT_FOUND;
import static is.hello.supichi.commandhandlers.ErrorText.ERROR_DATA_TOO_OLD;
import static is.hello.supichi.commandhandlers.ErrorText.ERROR_NO_DATA;


/**
 * Created by ksg on 6/17/16
 */
public class RoomConditionsHandler extends BaseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomConditionsHandler.class);

    private static final String DEFAULT_SENSOR_UNIT = "f";
    private static final Float NO_SOUND_FILL_VALUE_DB = (float) 35; // Replace with this value when Sense isn't capturing audio
    private static final String ROOM_CONDITION_PATTERN = "(bed)?room('s)? condition";

    private static final Integer THRESHOLD_IN_MINUTES = 15;


    private final SpeechCommandDAO speechCommandDAO;
    private final DeviceDataDAODynamoDB deviceDataDAODynamoDB;
    private final DeviceDAO deviceDAO;
    private final SenseColorDAO senseColorDAO;
    private final CalibrationDAO calibrationDAO;
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

    public RoomConditionsHandler(final SpeechCommandDAO speechCommandDAO,
                                 final DeviceDataDAODynamoDB deviceDataDAODynamoDB,
                                 final DeviceDAO deviceDAO,
                                 final SenseColorDAO senseColorDAO,
                                 final CalibrationDAO calibrationDAO,
                                 final SensorViewLogic sensorViewLogic) {
        super("room_condition", speechCommandDAO, getAvailableActions());
        this.speechCommandDAO = speechCommandDAO;
        this.deviceDataDAODynamoDB = deviceDataDAODynamoDB;
        this.deviceDAO = deviceDAO;
        this.senseColorDAO = senseColorDAO;
        this.calibrationDAO = calibrationDAO;
        this.sensorViewLogic = sensorViewLogic;
    }

    private static Map<String, SpeechCommand> getAvailableActions() {
        final Map<String, SpeechCommand> tempMap = Maps.newHashMap();
        tempMap.put("the temperature", SpeechCommand.ROOM_TEMPERATURE);
        tempMap.put("the humidity", SpeechCommand.ROOM_HUMIDITY);
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
        final String text = annotatedTranscript.transcript;

        final Optional<SpeechCommand> optionalCommand = getCommand(text); // TODO: ensure that only valid commands are returned

        if (optionalCommand.isPresent()) {
            // TODO: get units preference
            return getCurrentRoomConditionsV15(request.accountId, optionalCommand.get(), DEFAULT_SENSOR_UNIT);
        }
        return new HandlerResult(HandlerType.ROOM_CONDITIONS, HandlerResult.EMPTY_COMMAND, GenericResult.fail(COMMAND_NOT_FOUND));
    }

    @Override
    public Integer matchAnnotations(final AnnotatedTranscript annotatedTranscript) {
        // TODO: add SensorAnnotation
        return NO_ANNOTATION_SCORE;
    }

    private HandlerResult getCurrentRoomConditionsV15(final Long accountId, final SpeechCommand command, final String unit) {

        final String sensorName = getSensorName(command);
        if (sensorName.isEmpty()) {
            return new HandlerResult(HandlerType.ROOM_CONDITIONS, command.getValue(), GenericResult.fail("invalid sensor"));
        }

        final DateTime asOfUTC = DateTime.now(DateTimeZone.UTC);
        final SensorResponse sensorResponse = sensorViewLogic.list(accountId, asOfUTC);

        switch (sensorResponse.status()) {
            case NO_SENSE:
                LOGGER.error("error=no-sensor-data reason=no-paired-sense account_id={}", accountId);
                return new HandlerResult(HandlerType.ROOM_CONDITIONS, command.getValue(), GenericResult.fail("no paired sense"));
            case WAITING_FOR_DATA:
                LOGGER.error("error=no-sensor-data reason=data-too-old account_id={} as_of_utc_now={}", accountId, asOfUTC);
                return new HandlerResult(HandlerType.ROOM_CONDITIONS, command.getValue(), GenericResult.fail(ERROR_DATA_TOO_OLD));
            case OK:
                return okSensorResult(accountId, command, sensorResponse, unit);
        }

        // uh-oh, something wrong
        LOGGER.error("error=fail-to-get-sensor-data account_id={} as_of_utc_now={}", accountId, asOfUTC);
        return new HandlerResult(HandlerType.ROOM_CONDITIONS, command.getValue(), GenericResult.fail(ERROR_NO_DATA));
    }

    private HandlerResult okSensorResult(final Long accountId, final SpeechCommand command, final SensorResponse sensorResponse, final String unit) {

        final Map<Sensor, SensorView> sensorViewMap = sensorResponse.sensors().stream()
                .collect(Collectors.toMap(SensorView::sensor, item -> item));

        final String sensorName = getSensorName(command);

        // current room state command
        if (command.equals(SpeechCommand.ROOM_CONDITION)) {
            final Condition condition = sensorResponse.condition();

            final RoomConditionResult roomResult = new RoomConditionResult(sensorName, condition.toString(), "", condition );
            final String responseText = String.format("Your room condition is in the %s state", condition.toString());
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
            final float temperatureValue = Math.round(sensorView.value());
            if (unit.equalsIgnoreCase("f")) {
                // cause we're special
                sensorUnit = SensorUnit.FAHRENHEIT.value();
                sensorValue = String.valueOf(celsiusToFahrenheit(temperatureValue));
            } else {
                sensorUnit = SensorUnit.CELSIUS.value();
                sensorValue = String.valueOf(Math.round(temperatureValue));
            }
        } else {
            sensorValue = String.valueOf(Math.round(sensorView.value()));
            sensorUnit = sensorView.unit().value();
        }

        LOGGER.debug("action=get-room-condition-ok command={} value={}, unit={}", command.toString(), sensorValue, sensorUnit);

        final RoomConditionResult roomResult = new RoomConditionResult(sensorName, sensorValue, sensorUnit, Condition.UNKNOWN);
        final String responseText = String.format("The %s in your room is %s %s", sensorName, sensorValue, sensorUnit);

        return HandlerResult.withRoomResult(HandlerType.ROOM_CONDITIONS, command.getValue(), GenericResult.ok(responseText), roomResult);
    }


    // sense 1.0
    @Deprecated
    private HandlerResult getCurrentRoomConditions(final Long accountId, final SpeechCommand command, final String unit) {

        final Optional<DeviceAccountPair> optionalDeviceAccountPair = deviceDAO.getMostRecentSensePairByAccountId(accountId);
        if (!optionalDeviceAccountPair.isPresent()) {
            return new HandlerResult(HandlerType.ROOM_CONDITIONS, command.getValue(), GenericResult.fail("no paired sense"));
        }

        final String senseId = optionalDeviceAccountPair.get().externalDeviceId;

        // TODO: check sensor view available? Or just return no data response

        // look back last 30 minutes
        Integer mostRecentLookBackMinutes = 30;
        final DateTime maxDT = DateTime.now(DateTimeZone.UTC).plusMinutes(2);
        final DateTime minDT = DateTime.now(DateTimeZone.UTC).minusMinutes(mostRecentLookBackMinutes);

        final Optional<DeviceData> optionalData = deviceDataDAODynamoDB.getMostRecent(accountId, senseId, maxDT, minDT);
        if (!optionalData.isPresent()) {
            return new HandlerResult(HandlerType.ROOM_CONDITIONS, command.getValue(), GenericResult.fail(ERROR_NO_DATA));
        }

        final String sensorName = getSensorName(command);
        if (sensorName.isEmpty()) {
            return new HandlerResult(HandlerType.ROOM_CONDITIONS, command.getValue(), GenericResult.fail("invalid sensor"));
        }

        final Optional<Device.Color> color = senseColorDAO.getColorForSense(senseId);
        final DeviceData deviceData = optionalData.get().withCalibratedLight(color); // with light calibration
        final Optional<Calibration> calibrationOptional = calibrationDAO.getStrict(senseId);

        final CurrentRoomState roomState = CurrentRoomState.fromDeviceData(deviceData, DateTime.now(), THRESHOLD_IN_MINUTES, unit, calibrationOptional, NO_SOUND_FILL_VALUE_DB);
        if (roomState.temperature().condition().equals(Condition.UNKNOWN)) {
            return new HandlerResult(HandlerType.ROOM_CONDITIONS, command.getValue(), GenericResult.fail(ERROR_DATA_TOO_OLD));
        }

        final Condition condition = RoomConditionUtil.getGeneralRoomCondition(roomState);
        final String sensorValue;
        final String sensorUnit;
        switch (command) {
            case ROOM_CONDITION:
                sensorUnit = "";
                sensorValue = condition.toString();
                break;
            case ROOM_TEMPERATURE:
                if (unit.equalsIgnoreCase("f")) {
                    sensorUnit = "ºF";
                    sensorValue = String.valueOf(celsiusToFahrenheit(roomState.temperature().value));

                } else {
                    sensorUnit = "ºC";
                    sensorValue = String.valueOf(Math.round(roomState.temperature().value));
                }
                break;
            case ROOM_HUMIDITY:
                sensorValue = String.valueOf(Math.round(roomState.humidity().value));
                sensorUnit = "percent";
                break;
            case ROOM_LIGHT:
                sensorValue = String.valueOf(Math.round(roomState.light().value));
                sensorUnit = "lux";
                break;
            case ROOM_SOUND:
                sensorValue = String.valueOf(Math.round(roomState.sound().value));
                sensorUnit = "decibels";
                break;
            case PARTICULATES:
                sensorValue = String.valueOf(Math.round(roomState.particulates().value));
                sensorUnit = "micro grams per cubic meter";
                break;
            default:
                sensorValue = "";
                sensorUnit = "";
                break;
        }

        LOGGER.debug("action=get-room-condition command={} value={}", command.toString(), sensorValue);

        final RoomConditionResult roomResult = new RoomConditionResult(sensorName, sensorValue, sensorUnit, condition);
        final String responseText = String.format("The %s in your room is %s %s", sensorName, sensorValue, sensorUnit);

        return HandlerResult.withRoomResult(HandlerType.ROOM_CONDITIONS, command.getValue(), GenericResult.ok(responseText), roomResult);
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
}
