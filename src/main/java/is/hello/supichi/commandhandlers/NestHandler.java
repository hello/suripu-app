package is.hello.supichi.commandhandlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.hello.suripu.core.models.ValueRange;
import com.hello.suripu.core.preferences.TemperatureUnit;
import com.hello.suripu.core.speech.interfaces.Vault;
import is.hello.gaibu.core.models.Expansion;
import is.hello.gaibu.core.models.ExpansionData;
import is.hello.gaibu.core.stores.PersistentExpansionDataStore;
import is.hello.gaibu.core.stores.PersistentExpansionStore;
import is.hello.gaibu.core.stores.PersistentExternalTokenStore;
import is.hello.gaibu.homeauto.clients.NestThermostat;
import is.hello.gaibu.homeauto.models.AlarmActionStatus;
import is.hello.gaibu.homeauto.models.NestExpansionDeviceData;
import is.hello.supichi.commandhandlers.results.GenericResult;
import is.hello.supichi.commandhandlers.results.NestResult;
import is.hello.supichi.db.SpeechCommandDAO;
import is.hello.supichi.models.AnnotatedTranscript;
import is.hello.supichi.models.HandlerResult;
import is.hello.supichi.models.HandlerType;
import is.hello.supichi.models.SpeechCommand;
import is.hello.supichi.models.VoiceRequest;
import is.hello.supichi.response.SupichiResponseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static is.hello.supichi.commandhandlers.ErrorText.COMMAND_NOT_FOUND;
import static is.hello.supichi.commandhandlers.ErrorText.EXPANSION_NOT_FOUND;


public class NestHandler extends BaseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(NestHandler.class);

    private static final String TEMP_SET_PATTERN_WORDS = "(?i)^.*(?:nest|thermostat|temp|temperature)?\\sto\\s(\\w+)\\s(\\w+)\\sdegrees";
    private static final String TEMP_SET_PATTERN_NUMERIC = "(?i)^.*(?:nest|thermostat|temp|temperature)?\\sto\\s(\\d+)(\\sdegrees)?";
    private static final String TOGGLE_ACTIVE_PATTERN = "(?i)^.*turn.*(?:nest|thermostat)?\\s(on|off).*(?:nest|thermostat)?";

    public static final String SET_TEMP_OK_RESPONSE = "Okay, done";
    public static final String SET_TEMP_ERROR_RESPONSE = "Sorry, your thermostat could not be reached";
    public static final String SET_TEMP_ERROR_LOCKED_RESPONSE = "The temperature could not be adjusted because your thermostat is locked or off.";
    public static final String SET_TEMP_ERROR_AUTH = "Please connect your thermostat on the Sense app under Expansions";
    public static final String SET_TEMP_ERROR_CONFIG = "Please connect your thermostat on the Sense app under Expansions";
    public static final String SET_TEMP_ERROR_APPLICATION = "Sorry, your thermostat could not be reached";

    private final SpeechCommandDAO speechCommandDAO;
    private final PersistentExternalTokenStore externalTokenStore;
    private final PersistentExpansionStore externalApplicationStore;
    private final Vault tokenKMSVault;
    private Optional<Expansion> expansionOptional;
    private final PersistentExpansionDataStore externalAppDataStore;
    private ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Integer> numberWords;

    public NestHandler(final SpeechCommandDAO speechCommandDAO,
                       final PersistentExternalTokenStore externalTokenStore,
                       final PersistentExpansionStore externalApplicationStore,
                       final PersistentExpansionDataStore externalAppDataStore,
                       final Vault tokenKMSVault) {
        super("nest_thermostat", speechCommandDAO, getAvailableActions());
        this.speechCommandDAO = speechCommandDAO;
        this.externalTokenStore = externalTokenStore;
        this.externalApplicationStore = externalApplicationStore;
        this.externalAppDataStore = externalAppDataStore;
        this.tokenKMSVault = tokenKMSVault;
        numberWords = Maps.newHashMap();
        init();
    }

    private void init() {
        final Optional<Expansion> externalApplicationOptional = externalApplicationStore.getApplicationByName(Expansion.ServiceName.NEST.toString());
        if(!externalApplicationOptional.isPresent()) {
            LOGGER.error("error=application-not-found app_name=Nest");
        }
        expansionOptional = externalApplicationOptional;

        numberWords.put("one", 1);
        numberWords.put("two", 2);
        numberWords.put("three", 3);
        numberWords.put("four", 4);
        numberWords.put("five", 5);
        numberWords.put("six", 6);
        numberWords.put("seven", 7);
        numberWords.put("eight", 8);
        numberWords.put("nine", 9);
        numberWords.put("ten", 10);
        numberWords.put("twenty", 20);
        numberWords.put("thirty", 30);
        numberWords.put("forty", 40);
        numberWords.put("fifty", 50);
        numberWords.put("sixty", 60);
        numberWords.put("seventy", 70);
        numberWords.put("eighty", 80);
        numberWords.put("ninety", 90);
    }

    private static Map<String, SpeechCommand> getAvailableActions() {
        // TODO read from DynamoDB
        final Map<String, SpeechCommand> tempMap = Maps.newHashMap();
        tempMap.put(TEMP_SET_PATTERN_WORDS, SpeechCommand.THERMOSTAT_SET);
        tempMap.put(TEMP_SET_PATTERN_NUMERIC, SpeechCommand.THERMOSTAT_SET);
        return tempMap;
    }


    private HandlerResult returnWithErrorMaybe(final SpeechCommand command, final AlarmActionStatus status, final Integer temperatureSum) {
        final NestResult actualNestResult = new NestResult(temperatureSum.toString());
        switch(status) {
            case OK:
                final GenericResult nestResult = GenericResult.ok(SET_TEMP_OK_RESPONSE);
                return HandlerResult.withNestResult(HandlerType.NEST, command.getValue(), nestResult, actualNestResult);
            case OFF_OR_LOCKED:
                final GenericResult lockedResult = GenericResult.ok(SET_TEMP_ERROR_LOCKED_RESPONSE);
                return HandlerResult.withNestResult(HandlerType.NEST, command.getValue(), lockedResult, actualNestResult);
        }

        final GenericResult nestResult = GenericResult.failWithResponse("command failed", SET_TEMP_ERROR_RESPONSE);
        return HandlerResult.withNestResult(HandlerType.NEST, command.getValue(), nestResult, actualNestResult);
    }

    @Override
    public HandlerResult executeCommand(final AnnotatedTranscript annotatedTranscript, final VoiceRequest request) {

        final String senseId = request.senseId;
        final Long accountId = request.accountId;

        GenericResult nestResult;

        if(!expansionOptional.isPresent()) {
            LOGGER.error("error=application-not-found app_name=Nest");
            nestResult = GenericResult.failWithResponse(EXPANSION_NOT_FOUND, SET_TEMP_ERROR_APPLICATION);
            return new HandlerResult(HandlerType.NEST, HandlerResult.EMPTY_COMMAND, nestResult);
        }

        final Expansion expansion = expansionOptional.get();
        final String text = annotatedTranscript.lowercaseTranscript();
        final Optional<SpeechCommand> optionalCommand = getCommand(annotatedTranscript); // TODO: ensure that only valid commands are returned

        if (!optionalCommand.isPresent()) {
            LOGGER.error("error=no-command app_name=nest text={}", text);
            nestResult = GenericResult.failWithResponse(COMMAND_NOT_FOUND, SET_TEMP_ERROR_RESPONSE);
            return new HandlerResult(HandlerType.NEST, HandlerResult.EMPTY_COMMAND, nestResult);
        }

        final SpeechCommand command = optionalCommand.get();

        if (senseId == null) {
            LOGGER.error("error=null-sense-id app_name=nest");
            nestResult = GenericResult.failWithResponse("sense_id not found", SET_TEMP_ERROR_RESPONSE);
            return new HandlerResult(HandlerType.NEST, command.getValue(), nestResult);
        }

        final Optional<String> decryptedTokenOptional = externalTokenStore.getDecryptedExternalToken(senseId, expansion, false);
        if(!decryptedTokenOptional.isPresent()) {
            LOGGER.error("error=token-decryption-failure sense_id={}", senseId);
            nestResult = GenericResult.failWithResponse("token decrypt failed", SET_TEMP_ERROR_AUTH);
            return new HandlerResult(HandlerType.NEST, command.getValue(), nestResult);
        }

        final String decryptedToken = decryptedTokenOptional.get();

        final Optional<ExpansionData> extAppDataOptional = externalAppDataStore.getAppData(expansion.id, senseId);
        if(!extAppDataOptional.isPresent()) {
            LOGGER.error("error=no-ext-app-data account_id={}", accountId);
            nestResult = GenericResult.failWithResponse("no expansion data", SET_TEMP_ERROR_CONFIG);
            return new HandlerResult(HandlerType.NEST, command.getValue(), nestResult);
        }

        final ExpansionData extData = extAppDataOptional.get();

        if(extData.data.isEmpty()) {
            LOGGER.error("error=no-ext-app-data account_id={}", accountId);
            nestResult = GenericResult.failWithResponse("no expansion data", SET_TEMP_ERROR_CONFIG);
            return new HandlerResult(HandlerType.NEST, command.getValue(), nestResult);
        }

        NestThermostat nest;
        try {
            final NestExpansionDeviceData nestData = mapper.readValue(extData.data, NestExpansionDeviceData.class);
            nest = NestThermostat.create(expansion.apiURI, decryptedToken, nestData.thermostatId);

        } catch (IOException io) {
            LOGGER.warn("error=bad-app-data app_name=nest sense_id={}", senseId);
            nestResult = GenericResult.failWithResponse("bad expansion data", SET_TEMP_ERROR_CONFIG);
            return new HandlerResult(HandlerType.NEST, command.getValue(), nestResult);
        }

        if (command.equals(SpeechCommand.THERMOSTAT_SET)) {
            Integer temperatureSum = 0;

            final Pattern words = Pattern.compile(TEMP_SET_PATTERN_WORDS);
            Matcher m = words.matcher(text);
            if (m.find( )) {
                if(numberWords.containsKey(m.group(1))) {
                    temperatureSum += numberWords.get(m.group(1));
                }
                if(numberWords.containsKey(m.group(2))) {
                    temperatureSum += numberWords.get(m.group(2));
                }

                //This will at least ensure units that make sense (C:9-32deg; F:50-90deg)
                //We could query for the Nest's temp units, but it may not agree with the Sense account
                //or there's the case where someone else tries to set the temperature and doesn't know the units of either
                final TemperatureUnit units = (temperatureSum > 49) ? TemperatureUnit.FAHRENHEIT : TemperatureUnit.CELSIUS;
                final ValueRange values = new ValueRange(temperatureSum - NestThermostat.TARGET_TEMP_RANGE_BUFFER, temperatureSum + NestThermostat.TARGET_TEMP_RANGE_BUFFER);

                final AlarmActionStatus status = nest.setTempFromValueRange(values, units);
                return returnWithErrorMaybe(command, status, temperatureSum);
            }

            final Pattern numeric = Pattern.compile(TEMP_SET_PATTERN_NUMERIC);
            m = numeric.matcher(text);
            if (m.find( )) {
                temperatureSum += Integer.parseInt(m.group(1));

                final TemperatureUnit units = (temperatureSum > 49) ? TemperatureUnit.FAHRENHEIT : TemperatureUnit.CELSIUS;
                final ValueRange values = new ValueRange(temperatureSum - NestThermostat.TARGET_TEMP_RANGE_BUFFER, temperatureSum + NestThermostat.TARGET_TEMP_RANGE_BUFFER);
                final AlarmActionStatus status = nest.setTempFromValueRange(values, units);
                LOGGER.info("action=set-temperature-numeric status={} account_id={} sense_id={}", status, request.accountId, request.senseId);

                return returnWithErrorMaybe(command, status, temperatureSum);
            }
        }

        if (optionalCommand.get().equals(SpeechCommand.THERMOSTAT_ACTIVE)) {
            final Pattern r = Pattern.compile(TOGGLE_ACTIVE_PATTERN);
            Matcher m = r.matcher(text);
            Boolean isOn = false;
            if (m.find( )) {
                isOn = m.group(1).startsWith("on");

            } else {
                LOGGER.warn("error=no-pattern-match app_name=nest sense_id={}", senseId);
                nestResult = GenericResult.failWithResponse("no pattern match", SET_TEMP_ERROR_RESPONSE);
                return new HandlerResult(HandlerType.NEST, command.getValue(), nestResult);
            }
        }

        LOGGER.warn("error=no-pattern-match app_name=nest sense_id={}", senseId);
        nestResult = GenericResult.failWithResponse(COMMAND_NOT_FOUND, SET_TEMP_ERROR_RESPONSE);
        return new HandlerResult(HandlerType.NEST, command.getValue(), nestResult);
    }

    @Override
    public Integer matchAnnotations(final AnnotatedTranscript annotatedTranscript) {
        // TODO: add temperature
        return NO_ANNOTATION_SCORE;
    }

    @Override
    public SupichiResponseType responseType() {
        return SupichiResponseType.WATSON;
    }

}
