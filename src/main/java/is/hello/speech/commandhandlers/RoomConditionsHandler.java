package is.hello.speech.commandhandlers;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
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
import is.hello.speech.db.SpeechCommandDAO;
import is.hello.speech.commandhandlers.results.GenericResult;
import is.hello.speech.commandhandlers.results.Outcome;
import is.hello.speech.commandhandlers.results.RoomConditionResult;
import is.hello.speech.models.AnnotatedTranscript;
import is.hello.speech.models.HandlerResult;
import is.hello.speech.models.HandlerType;
import is.hello.speech.models.SpeechCommand;
import is.hello.speech.models.VoiceRequest;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static is.hello.speech.commandhandlers.ErrorText.COMMAND_NOT_FOUND;
import static is.hello.speech.commandhandlers.ErrorText.ERROR_DATA_TOO_OLD;
import static is.hello.speech.commandhandlers.ErrorText.ERROR_NO_DATA;


/**
 * Created by ksg on 6/17/16
 */
public class RoomConditionsHandler extends BaseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomConditionsHandler.class);

    private static final String DEFAULT_SENSOR_UNIT = "f";
    private static final Float NO_SOUND_FILL_VALUE_DB = (float) 35; // Replace with this value when Sense isn't capturing audio


    private final SpeechCommandDAO speechCommandDAO;
    private final DeviceDataDAODynamoDB deviceDataDAODynamoDB;
    private final DeviceDAO deviceDAO;
    private final SenseColorDAO senseColorDAO;
    private final CalibrationDAO calibrationDAO;

    public RoomConditionsHandler(final SpeechCommandDAO speechCommandDAO,
                                 final DeviceDataDAODynamoDB deviceDataDAODynamoDB,
                                 final DeviceDAO deviceDAO,
                                 final SenseColorDAO senseColorDAO,
                                 final CalibrationDAO calibrationDAO) {
        super("room_condition", speechCommandDAO, getAvailableActions());
        this.speechCommandDAO = speechCommandDAO;
        this.deviceDataDAODynamoDB = deviceDataDAODynamoDB;
        this.deviceDAO = deviceDAO;
        this.senseColorDAO = senseColorDAO;
        this.calibrationDAO = calibrationDAO;
    }

    private static Map<String, SpeechCommand> getAvailableActions() {
        // TODO read from DynamoDB
        final Map<String, SpeechCommand> tempMap = Maps.newHashMap();
        tempMap.put("the temperature", SpeechCommand.ROOM_TEMPERATURE);
        tempMap.put("the humidity", SpeechCommand.ROOM_HUMIDITY);
        tempMap.put("light level", SpeechCommand.ROOM_LIGHT);
        tempMap.put("how bright", SpeechCommand.ROOM_LIGHT);
        tempMap.put("sound level", SpeechCommand.ROOM_SOUND);
        tempMap.put("how noisy", SpeechCommand.ROOM_SOUND);
        tempMap.put("air quality", SpeechCommand.PARTICULATES);
        return tempMap;
    }


    @Override
    public HandlerResult executeCommand(final AnnotatedTranscript annotatedTranscript, final VoiceRequest request) {
        final String text = annotatedTranscript.transcript;

        final Optional<SpeechCommand> optionalCommand = getCommand(text); // TODO: ensure that only valid commands are returned

        if (optionalCommand.isPresent()) {
            // TODO: get units preference
            return getCurrentRoomConditions(request.accountId, optionalCommand.get(), DEFAULT_SENSOR_UNIT);
        }
        return new HandlerResult(HandlerType.ROOM_CONDITIONS, HandlerResult.EMPTY_COMMAND, GenericResult.fail(COMMAND_NOT_FOUND));
    }

    @Override
    public Integer matchAnnotations(final AnnotatedTranscript annotatedTranscript) {
        // TODO: add SensorAnnotation
        return NO_ANNOTATION_SCORE;
    }


    private HandlerResult getCurrentRoomConditions(final Long accountId, final SpeechCommand command, final String unit) {
        final Map<String, String> response = Maps.newHashMap();

        final Optional<DeviceAccountPair> optionalDeviceAccountPair = deviceDAO.getMostRecentSensePairByAccountId(accountId);
        if (!optionalDeviceAccountPair.isPresent()) {
            return new HandlerResult(HandlerType.ROOM_CONDITIONS, command.getValue(), GenericResult.fail("no paired sense"));
        }

        final String senseId = optionalDeviceAccountPair.get().externalDeviceId;

        // TODO: check sensor view available? Or just return no data response

        // look back last 30 minutes
        Integer thresholdInMinutes = 15;
        Integer mostRecentLookBackMinutes = 30;
        final DateTime maxDT = DateTime.now(DateTimeZone.UTC).plusMinutes(2);
        final DateTime minDT = DateTime.now(DateTimeZone.UTC).minusMinutes(mostRecentLookBackMinutes);

        final Optional<DeviceData> optionalData = deviceDataDAODynamoDB.getMostRecent(accountId, senseId, maxDT, minDT);
        if (!optionalData.isPresent()) {
            return new HandlerResult(HandlerType.ROOM_CONDITIONS, command.getValue(), GenericResult.fail(ERROR_NO_DATA));
        }

        final String sensorName = getSensorName(command);
        if (sensorName.isEmpty()) {
            response.put("result", Outcome.FAIL.getValue());
            response.put("error", "invalid sensor");
            return new HandlerResult(HandlerType.ROOM_CONDITIONS, command.getValue(), GenericResult.fail("invalid sensor"));
        }

        final Optional<Device.Color> color = senseColorDAO.getColorForSense(senseId);
        final DeviceData deviceData = optionalData.get().withCalibratedLight(color); // with light calibration
        final Optional<Calibration> calibrationOptional = calibrationDAO.getStrict(senseId);

        final CurrentRoomState roomState = CurrentRoomState.fromDeviceData(deviceData, DateTime.now(), thresholdInMinutes, unit, calibrationOptional, NO_SOUND_FILL_VALUE_DB);
        if (roomState.temperature().condition().equals(Condition.UNKNOWN)) {
            return new HandlerResult(HandlerType.ROOM_CONDITIONS, command.getValue(), GenericResult.fail(ERROR_DATA_TOO_OLD));
        }

        final String sensorValue;
        final String sensorUnit;
        switch (command) {
            case ROOM_TEMPERATURE:
                if (unit.equalsIgnoreCase("f")) {
                    sensorUnit = "ºF";
                    sensorValue = String.valueOf(celsiusToFahrenheit(roomState.temperature().value));

                } else {
                    sensorUnit = "ºF";
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

        final RoomConditionResult roomResult = new RoomConditionResult(sensorName, sensorValue, sensorUnit);
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
        }
        return "";
    }
}
