package is.hello.supichi.models.responsebuilder;

import com.google.common.collect.Maps;
import com.hello.suripu.core.models.Sensor;
import com.hello.suripu.core.roomstate.Condition;
import is.hello.supichi.commandhandlers.ErrorText;
import is.hello.supichi.commandhandlers.results.Outcome;
import is.hello.supichi.commandhandlers.results.RoomConditionResult;
import is.hello.supichi.models.HandlerResult;
import is.hello.supichi.api.Response;
import is.hello.supichi.models.SpeechCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Random;

/**
 * Created by ksg on 7/26/16
 */
public class RoomConditionsResponseBuilder implements ResponseBuilderInterface{
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomConditionsResponseBuilder.class);

    // MUST-HAVES
    private static final String BUCKET_NAME = "ROOM_CONDITIONS";
    private static final String FILENAME_PREFIX = "ROOM_CONDITIONS-GET_SENSOR";

    // Builder-related
    public static final String CUSTOM_ERROR_FORMATTER = "Sorry, I wasn't able to access your %s data right now. Please try again later";
    private static final String CUSTOM_ERROR_FILENAME_TEMPLATE = "-no_data-WATSON-%s-16k.wav";

    private static final String RESPONSE_TEXT_FORMATTER = "The %s in your room is %s %s.";

    private final static Map<String, Integer> numOptions;
    static {
        numOptions = Maps.newHashMap();
        numOptions.put(Sensor.TEMPERATURE.toString(), 4);
        numOptions.put(Sensor.HUMIDITY.toString(), 2);
        numOptions.put(Sensor.LIGHT.toString(), 2);
        numOptions.put(Sensor.SOUND.toString(), 2);
        numOptions.put(Sensor.PARTICULATES.toString(), 2);
    }

    public static BuilderResponse response(final HandlerResult handlerResult,
                                    final String voiceService,
                                    final String voiceName) {
        String s3Bucket = BUCKET_NAME;
        String filename = FILENAME_PREFIX;
        String sensorName = "";
        String responseText;

        if (!handlerResult.optionalRoomResult.isPresent()) {
            s3Bucket = DefaultResponseBuilder.BUCKET_NAME;
            filename = DefaultResponseBuilder.DEFAULT_KEYNAMES.get(Response.SpeechResponse.Result.TRY_AGAIN);
            responseText = DefaultResponseBuilder.DEFAULT_TEXT.get(Response.SpeechResponse.Result.TRY_AGAIN);
            return new BuilderResponse(s3Bucket, filename, responseText);
        }

        final RoomConditionResult roomResult = handlerResult.optionalRoomResult.get();
        sensorName = roomResult.sensorName.toUpperCase();
        filename += "-" + sensorName;
        s3Bucket += String.format("/%s", sensorName);

        // FAIL outcome
        if (handlerResult.outcome().equals(Outcome.FAIL)) {
            if (handlerResult.optionalErrorText().isPresent()) {
                final String errorMessage =  handlerResult.optionalErrorText().get();
                if (errorMessage.equalsIgnoreCase(ErrorText.ERROR_NO_DATA) || errorMessage.equalsIgnoreCase(ErrorText.ERROR_DATA_TOO_OLD)) {
                    // custom errors
                    filename += String.format(CUSTOM_ERROR_FILENAME_TEMPLATE, voiceName);
                    responseText = String.format(CUSTOM_ERROR_FORMATTER, sensorName);
                    return new BuilderResponse(s3Bucket, filename, responseText);
                }
            }

            // return generic error
            s3Bucket = DefaultResponseBuilder.BUCKET_NAME;
            filename = DefaultResponseBuilder.DEFAULT_KEYNAMES.get(Response.SpeechResponse.Result.REJECTED);
            responseText = DefaultResponseBuilder.DEFAULT_TEXT.get(Response.SpeechResponse.Result.REJECTED);
            return new BuilderResponse(s3Bucket, filename, responseText);
        }

        // OK outcome

        final String sensorValue = roomResult.sensorValue;
        String params = String.format("value_%s", sensorValue);

        // units
        final String unit = roomResult.sensorUnit;
        if (sensorName.equalsIgnoreCase(Sensor.TEMPERATURE.toString()) && unit.equals("ºF")) {
            params += "_F";
        }

        // general room condition command
        if (sensorName.equalsIgnoreCase(SpeechCommand.ROOM_CONDITION.getValue())) {
            final String condition;
            if (roomResult.condition.equals(Condition.IDEAL)) {
                responseText = "Your room is ready for a good night's sleep.";
                condition = "ideal";
            } else if (roomResult.condition.equals(Condition.WARNING)) {
                responseText = "Your room conditions are not ideal.";
                condition = "warn";
            } else {
                responseText = "Your room is in poor condition for sleep.";
                condition = "alert";
            }
            filename = String.format("ROOM_CONDITION-%s-%s-%s-16k.wav", condition, voiceService, voiceName);
            return new BuilderResponse(s3Bucket, filename, responseText);
        }

        // randomly pick one of the responses
        final int maxOptions = numOptions.get(sensorName.toLowerCase());
        if (maxOptions > 1) {
            final Random random = new Random();
            final int option = random.nextInt(maxOptions) + 1;
            params += String.format("-opt_%d", option);
        }

        // special cases
        if (sensorName.equalsIgnoreCase(Sensor.LIGHT.toString()) && sensorValue.equals("0")) {
            params = "under_1_lux-opt_1";
        }

        filename += String.format("-%s-%s-%s-16k.wav", params, voiceService, voiceName);
        responseText = String.format(RESPONSE_TEXT_FORMATTER, sensorName, sensorValue, unit);

        LOGGER.debug("action=get-filename name={}", filename);

        return new BuilderResponse(s3Bucket, filename, responseText);
    }

}
