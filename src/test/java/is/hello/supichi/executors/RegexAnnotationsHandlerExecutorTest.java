package is.hello.supichi.executors;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hello.suripu.app.sensors.SensorResponse;
import com.hello.suripu.app.sensors.SensorViewLogic;
import com.hello.suripu.core.db.AccountLocationDAO;
import com.hello.suripu.core.db.AlarmDAODynamoDB;
import com.hello.suripu.core.db.MergedUserInfoDynamoDB;
import com.hello.suripu.core.db.SleepStatsDAODynamoDB;
import com.hello.suripu.core.db.TimeZoneHistoryDAODynamoDB;
import com.hello.suripu.core.models.TimeZoneHistory;
import com.hello.suripu.core.models.ValueRange;
import com.hello.suripu.core.models.sleep_sounds.Sound;
import com.hello.suripu.core.preferences.AccountPreferencesDynamoDB;
import com.hello.suripu.core.processors.SleepSoundsProcessor;
import com.hello.suripu.core.speech.interfaces.Vault;
import com.hello.suripu.coredropwizard.clients.MessejiClient;
import com.hello.suripu.coredropwizard.timeline.InstrumentedTimelineProcessor;
import is.hello.gaibu.core.models.Expansion;
import is.hello.gaibu.core.models.ExpansionData;
import is.hello.gaibu.core.models.ExternalToken;
import is.hello.gaibu.core.models.MultiDensityImage;
import is.hello.gaibu.core.stores.PersistentExpansionDataStore;
import is.hello.gaibu.core.stores.PersistentExpansionStore;
import is.hello.gaibu.core.stores.PersistentExternalTokenStore;
import is.hello.supichi.commandhandlers.AlarmHandler;
import is.hello.supichi.commandhandlers.BaseHandler;
import is.hello.supichi.commandhandlers.HandlerFactory;
import is.hello.supichi.commandhandlers.HueHandler;
import is.hello.supichi.commandhandlers.NestHandler;
import is.hello.supichi.commandhandlers.RoomConditionsHandler;
import is.hello.supichi.commandhandlers.SleepSoundHandler;
import is.hello.supichi.commandhandlers.SleepSummaryHandler;
import is.hello.supichi.commandhandlers.TimeHandler;
import is.hello.supichi.commandhandlers.results.Outcome;
import is.hello.supichi.db.SpeechCommandDAO;
import is.hello.supichi.models.AnnotatedTranscript;
import is.hello.supichi.models.Annotator;
import is.hello.supichi.models.HandlerResult;
import is.hello.supichi.models.HandlerType;
import is.hello.supichi.models.SpeechCommand;
import is.hello.supichi.models.VoiceRequest;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static is.hello.supichi.models.SpeechCommand.ALARM_DELETE;
import static is.hello.supichi.models.SpeechCommand.ALARM_GET;
import static is.hello.supichi.models.SpeechCommand.ALARM_SET;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class RegexAnnotationsHandlerExecutorTest {
    private final SpeechCommandDAO speechCommandDAO = mock(SpeechCommandDAO.class);
    private final PersistentExternalTokenStore externalTokenStore = mock(PersistentExternalTokenStore.class);
    private final PersistentExternalTokenStore badTokenStore = mock(PersistentExternalTokenStore.class);
    private final PersistentExpansionStore externalApplicationStore = mock(PersistentExpansionStore.class);
    private final PersistentExpansionDataStore externalAppDataStore = mock(PersistentExpansionDataStore.class);
    private final TimeZoneHistoryDAODynamoDB timeZoneHistoryDAODynamoDB = mock(TimeZoneHistoryDAODynamoDB.class);
    private final Vault tokenKMSVault = mock(Vault.class);
    private final AlarmDAODynamoDB alarmDAO = mock(AlarmDAODynamoDB.class);
    private final MergedUserInfoDynamoDB mergedUserDAO = mock(MergedUserInfoDynamoDB.class);
    private final SleepStatsDAODynamoDB sleepStatsDAODynamoDB = mock(SleepStatsDAODynamoDB.class);
    private final InstrumentedTimelineProcessor timelineProcessor = mock(InstrumentedTimelineProcessor.class);

    private final MessejiClient messejiClient = mock(MessejiClient.class);
    private final SleepSoundsProcessor sleepSoundsProcessor = mock(SleepSoundsProcessor.class);
    private final AccountLocationDAO accountLocationDAO = mock(AccountLocationDAO.class);
    private final SensorViewLogic sensorViewLogic = mock(SensorViewLogic.class);
    private final AccountPreferencesDynamoDB accountPreferenceDAO = mock(AccountPreferencesDynamoDB.class);


    private final String SENSE_ID = "123456789";
    private final Long ACCOUNT_ID = 99L;
    private final DateTimeZone TIME_ZONE = DateTimeZone.forID("America/Los_Angeles");

    private static final Sound DEFAULT_SOUND = Sound.create(20L,
            "https://s3.amazonaws.com/hello-audio/sleep-tones-preview/Rainfall.mp3",
            "Rainfall",
            "/SLPTONES/ST006.RAW",
            "s3://hello-audio/sleep-tones-raw/2016-04-01/ST006.raw"
    );

    @Before
    public void setUp() {
        final ExpansionData fakeHueApplicationData = new ExpansionData.Builder()
            .withAppId(1L)
            .withDeviceId(SENSE_ID)
            .withData("{\"whitelist_id\":\"123abc\", \"bridge_id\":\"fake_bridge\", \"group_id\": 1}")
            .withCreated(DateTime.now())
            .withEnabled(true)
            .build();

        final ExpansionData fakeNestApplicationData = new ExpansionData.Builder()
            .withAppId(2L)
            .withDeviceId(SENSE_ID)
            .withData("{\"thermostat_id\":\"123abc\"}")
            .withCreated(DateTime.now())
            .withEnabled(true)
            .build();

        String CLIENT_ID = "client_id";
        final MultiDensityImage icon = new MultiDensityImage("icon@1x.png", "icon@2x.png", "icon@3x.png");

        final Expansion fakeHueApplication = new Expansion(1L, Expansion.ServiceName.HUE,
            "Hue Light", "Phillips", "Fake Hue Application", icon, CLIENT_ID, "client_secret",
            "http://localhost/",  "auth_uri", "token_uri", "refresh_uri", Expansion.Category.LIGHT,
            DateTime.now(), 2, "completion_uri", Expansion.State.NOT_CONNECTED, ValueRange.createEmpty());

        final Expansion fakeNestApplication = new Expansion(2L, Expansion.ServiceName.NEST,
            "Nest Thermostat", "Nest", "Fake Nest Application", icon, CLIENT_ID, "client_secret",
            "http://localhost/",  "auth_uri", "token_uri", "refresh_uri", Expansion.Category.TEMPERATURE,
            DateTime.now(), 2, "completion_uri", Expansion.State.NOT_CONNECTED, ValueRange.createEmpty());

        final String fakeDecryptedToken = "fake_token";
        final ExternalToken fakeToken = new ExternalToken.Builder()
            .withAccessToken(fakeDecryptedToken)
            .withRefreshToken("fake_refresh")
            .withAccessExpiresIn(123456789L)
            .withRefreshExpiresIn(123456789L)
            .withDeviceId(SENSE_ID)
            .withAppId(1L)
            .build();
        final Map<String, String> encryptionContext = Maps.newHashMap();
        encryptionContext.put("application_id", fakeToken.appId.toString());

        Mockito.when(externalApplicationStore.getApplicationByName(Expansion.ServiceName.HUE.toString())).thenReturn(Optional.of(fakeHueApplication));
        Mockito.when(externalApplicationStore.getApplicationByName(Expansion.ServiceName.NEST.toString())).thenReturn(Optional.of(fakeNestApplication));
        Mockito.when(externalTokenStore.getTokenByDeviceId(Mockito.anyString(), Mockito.anyLong())).thenReturn(Optional.of(fakeToken));
        Mockito.when(externalTokenStore.getDecryptedExternalToken(Mockito.anyString(), Mockito.any(Expansion.class), Mockito.anyBoolean())).thenReturn(Optional.of(fakeDecryptedToken));
        Mockito.when(badTokenStore.getTokenByDeviceId(Mockito.anyString(), Mockito.anyLong())).thenReturn(Optional.absent());
        Mockito.when(badTokenStore.getDecryptedExternalToken(Mockito.anyString(), Mockito.any(Expansion.class), Mockito.anyBoolean())).thenReturn(Optional.absent());
        Mockito.when(tokenKMSVault.decrypt(fakeToken.accessToken, encryptionContext)).thenReturn(Optional.of(fakeToken.accessToken));
        Mockito.when(externalAppDataStore.getAppData(1L, SENSE_ID)).thenReturn(Optional.of(fakeHueApplicationData));
        Mockito.when(externalAppDataStore.getAppData(2L, SENSE_ID)).thenReturn(Optional.of(fakeNestApplicationData));

        final int offsetMillis = TIME_ZONE.getOffset(DateTime.now(DateTimeZone.UTC).getMillis());
        final Optional<TimeZoneHistory> optionalTimeZoneHistory = Optional.of(new TimeZoneHistory(offsetMillis, "America/Los_Angeles"));
        Mockito.when(timeZoneHistoryDAODynamoDB.getCurrentTimeZone(Mockito.anyLong())).thenReturn(optionalTimeZoneHistory);

        Mockito.when(mergedUserDAO.getInfo(SENSE_ID, ACCOUNT_ID)).thenReturn(Optional.absent());

        final SensorResponse sensorResponse = SensorResponse.noData(Collections.emptyList());
        Mockito.when(sensorViewLogic.list(Mockito.anyLong(), Mockito.anyObject())).thenReturn(sensorResponse);

        Mockito.when(sleepSoundsProcessor.getSoundByFileName(Mockito.anyString())).thenReturn(Optional.of(DEFAULT_SOUND));
    }

    private HandlerExecutor getExecutor() {
        final HandlerFactory handlerFactory = HandlerFactory.create(
                speechCommandDAO,
                messejiClient,
                sleepSoundsProcessor,
                timeZoneHistoryDAODynamoDB,
                "BLAH", // forecastio
                accountLocationDAO,
                externalTokenStore,
                externalApplicationStore,
                externalAppDataStore,
                tokenKMSVault,
                alarmDAO,
                mergedUserDAO,
                sleepStatsDAODynamoDB,
                timelineProcessor,
                Optional.absent(), // geoip DatabaseReader
                sensorViewLogic,
                accountPreferenceDAO,
                false
        );

        return new RegexAnnotationsHandlerExecutor(timeZoneHistoryDAODynamoDB)
                .register(HandlerType.ALARM, handlerFactory.alarmHandler())
                .register(HandlerType.SLEEP_SOUNDS, handlerFactory.sleepSoundHandler())
                .register(HandlerType.ROOM_CONDITIONS, handlerFactory.roomConditionsHandler())
                .register(HandlerType.TIME_REPORT, handlerFactory.timeHandler())
                .register(HandlerType.TRIVIA, handlerFactory.triviaHandler())
                .register(HandlerType.TIMELINE, handlerFactory.timelineHandler())
                .register(HandlerType.HUE, handlerFactory.hueHandler("sense-dev"))
                .register(HandlerType.NEST, handlerFactory.nestHandler())
                .register(HandlerType.SLEEP_SUMMARY, handlerFactory.sleepSummaryHandler());
    }

    private VoiceRequest newVoiceRequest(final String transcript) {
        return new VoiceRequest(SENSE_ID, ACCOUNT_ID, transcript, "127.0.0.1");
    }

    @Test
    public void TestAlarmHandlers() {
        // test handler mapping
        final HandlerExecutor handlerExecutor = getExecutor();
        HandlerResult result = handlerExecutor.handle(newVoiceRequest("set my alarm for 7 am"));
        assertEquals(result.handlerType, HandlerType.ALARM);
        assertEquals(result.optionalResult.isPresent(), true);
        assertEquals(result.command, ALARM_SET.getValue());

        result = handlerExecutor.handle(newVoiceRequest("wake me at 7 am"));
        assertEquals(result.handlerType, HandlerType.ALARM);
        assertEquals(result.optionalResult.isPresent(), true);
        assertEquals(result.command, ALARM_SET.getValue());

        result = handlerExecutor.handle(newVoiceRequest("wake her up at 7 am"));
        assertEquals(result.handlerType, HandlerType.NONE);

        result = handlerExecutor.handle(newVoiceRequest("alarm my dentist"));
        assertEquals(result.handlerType, HandlerType.NONE);

        // cancel alarm
        result = handlerExecutor.handle(newVoiceRequest("cancel my alarm"));
        assertEquals(result.handlerType, HandlerType.ALARM);
        assertEquals(result.optionalResult.isPresent(), true);
        assertEquals(result.command, ALARM_DELETE.getValue());

        result = handlerExecutor.handle(newVoiceRequest("delete tomorrow's alarm"));
        assertEquals(result.handlerType, HandlerType.ALARM);
        assertEquals(result.optionalResult.isPresent(), true);
        assertEquals(result.command, ALARM_DELETE.getValue());

        result = handlerExecutor.handle(newVoiceRequest("cancel all my appointments"));
        assertEquals(result.handlerType, HandlerType.NONE);
        assertEquals(result.optionalResult.isPresent(), false);

        // get alarm
        result = handlerExecutor.handle(newVoiceRequest("when is my alarm"));
        assertEquals(result.handlerType, HandlerType.ALARM);
        assertEquals(result.optionalResult.isPresent(), true);
        assertEquals(result.command, ALARM_GET.getValue());

    }

    private static class HandlerTestData{
        public final String text;
        public final Class klass;
        public final Boolean resultPresent;

        private HandlerTestData(final String text, final Class klass, final Boolean resultPresent) {
            this.text = text;
            this.klass = klass;
            this.resultPresent = resultPresent;
        }
    }

    @Test
    public void TestTextToHandler() {
        final List<HandlerTestData> dataList = Lists.newArrayList(
                new HandlerTestData("how are the conditions", RoomConditionsHandler.class, true),
                new HandlerTestData("what are the conditions in my room", RoomConditionsHandler.class, true),
                new HandlerTestData("how's my room conditions", RoomConditionsHandler.class, true),

                new HandlerTestData("turn off the alarm", AlarmHandler.class, true),

                new HandlerTestData("What time did I fall asleep", TimeHandler.class, false),
                new HandlerTestData("what time do I usually wake up", TimeHandler.class, false),
                new HandlerTestData("that says I do it all the time", TimeHandler.class, false),

                new HandlerTestData("What time are my alarm set for", AlarmHandler.class, true),
                new HandlerTestData("What time is my alarm set for", AlarmHandler.class, true),
                new HandlerTestData("What time is my alarm set", AlarmHandler.class, true),
                new HandlerTestData("What time is my alarm tomorrow", AlarmHandler.class, true),

                new HandlerTestData("what's the pressure", RoomConditionsHandler.class, true),
                new HandlerTestData("what's the co2 level", RoomConditionsHandler.class, true),
                new HandlerTestData("what's the carbon dioxide level", RoomConditionsHandler.class, true),

                new HandlerTestData("Play Sleep Sound", SleepSoundHandler.class, true),
                new HandlerTestData("play sleep sounds", SleepSoundHandler.class, true),

                new HandlerTestData("how's my sleep last night", SleepSummaryHandler.class, true),
                new HandlerTestData("how is my sleep last night", SleepSummaryHandler.class, true),
                new HandlerTestData("how did I sleep last night", SleepSummaryHandler.class, true),
                new HandlerTestData("how was my sleep", SleepSummaryHandler.class, true),
                new HandlerTestData("how the my sleep last night", SleepSummaryHandler.class, false),
                new HandlerTestData("how do I sleep", SleepSummaryHandler.class, true),
                new HandlerTestData("how long did I sleep", SleepSummaryHandler.class, true),
                new HandlerTestData("how many hours did I sleep", SleepSummaryHandler.class, true),
                new HandlerTestData("how much did I sleep", SleepSummaryHandler.class, true),

                new HandlerTestData("what's my score", SleepSummaryHandler.class, true),
                new HandlerTestData("what was my score", SleepSummaryHandler.class, true),
                new HandlerTestData("what is my sleep score", SleepSummaryHandler.class, true),

                new HandlerTestData("what's the temperature", RoomConditionsHandler.class, true),
                new HandlerTestData("how's the temperature", RoomConditionsHandler.class, true),
                new HandlerTestData("how is the temperature", RoomConditionsHandler.class, true),
                new HandlerTestData("how was the temperature", RoomConditionsHandler.class, true),
                new HandlerTestData("what temperature is it", RoomConditionsHandler.class, true),

                new HandlerTestData("what's the humidity", RoomConditionsHandler.class, true),
                new HandlerTestData("what humidity is it", RoomConditionsHandler.class, true),
                new HandlerTestData("how's the humidity", RoomConditionsHandler.class, true),
                new HandlerTestData("how is the humidity", RoomConditionsHandler.class, true),
                new HandlerTestData("how was the humidity", RoomConditionsHandler.class, true),

                // bad google transcript
                new HandlerTestData("what is the road condition", RoomConditionsHandler.class, true),
                new HandlerTestData("what's the road conditions", RoomConditionsHandler.class, true),



                new HandlerTestData("play Brown Noise", SleepSoundHandler.class, true),
                new HandlerTestData("play Cosmos", SleepSoundHandler.class, true),
                new HandlerTestData("play Autumn Wind", SleepSoundHandler.class, true),
                new HandlerTestData("play Fireside", SleepSoundHandler.class, true),
                new HandlerTestData("play Rainfall", SleepSoundHandler.class, true),
                new HandlerTestData("play White Noise", SleepSoundHandler.class, true),
                new HandlerTestData("play Forest Creek", SleepSoundHandler.class, true),
                new HandlerTestData("play Morpheus", SleepSoundHandler.class, true),
                new HandlerTestData("play Aura", SleepSoundHandler.class, true),
                new HandlerTestData("play Horizon", SleepSoundHandler.class, true),
                new HandlerTestData("play Nocturne", SleepSoundHandler.class, true),
                new HandlerTestData("play Ocean Waves", SleepSoundHandler.class, false),
                new HandlerTestData("play shit storm", SleepSoundHandler.class, false),
                new HandlerTestData("play ambient sounds", SleepSoundHandler.class, true)

        );

        final TimeZone timeZone = DateTimeZone.forID("America/Los_Angeles").toTimeZone();
        final HandlerExecutor handlerExecutor = getExecutor();
        for (final HandlerTestData data : dataList) {
            final AnnotatedTranscript transcript = Annotator.get(data.text, Optional.of(timeZone));
            Optional<BaseHandler> result = handlerExecutor.getHandler(transcript);
            assertEquals(data.text, result.isPresent(), data.resultPresent);
            if (result.isPresent()) {
                assertEquals(data.text, result.get().getClass(), data.klass);
                final BaseHandler handler = result.get();
                assertTrue(data.text, handler.getCommand(transcript).isPresent());
            }
        }

    }

    //Reproduce tests for UnigramHandlerExecutor to ensure regex executor doesn't break anything
    @Test
    public void TestHandleEmptyHandler() {
        final HandlerExecutor handlerExecutor = getExecutor();

        final HandlerResult result = handlerExecutor.handle(new VoiceRequest("123456789", 99L, "whatever", ""));
        assertEquals(result.handlerType, HandlerType.NONE);
    }

    @Test
    public void TestHandleSingleHandler() {
        final HandlerExecutor executor = getExecutor();

        final HandlerResult correctResult = executor.handle(new VoiceRequest("123456789", 99L, "best basketball", ""));
        assertEquals(correctResult.handlerType, HandlerType.TRIVIA);

        final HandlerResult result = executor.handle(new VoiceRequest("123456789", 99L, "whatever", ""));
        assertEquals(result.handlerType, HandlerType.NONE);
    }

    //Regex specific tests
    @Test
    public void TestHueHandler() {

        final HandlerExecutor executor = getExecutor();

        HandlerResult correctResult = executor.handle(newVoiceRequest("turn off the light"));
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.optionalHueResult.isPresent(), true);
        assertEquals(correctResult.optionalHueResult.get().lightOn, "false");

        correctResult = executor.handle(newVoiceRequest("turn the light off"));
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.optionalHueResult.isPresent(), true);
        assertEquals(correctResult.optionalHueResult.get().lightOn, "false");

        //test case insensitivity
        correctResult = executor.handle(newVoiceRequest("turn the Light On"));
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.optionalHueResult.isPresent(), true);
        assertEquals(correctResult.optionalHueResult.get().lightOn, "true");

        correctResult = executor.handle(newVoiceRequest("turn the Light Off"));
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.optionalHueResult.isPresent(), true);
        assertEquals(correctResult.optionalHueResult.get().lightOn, "false");

        correctResult = executor.handle(newVoiceRequest("turn off the light on"));
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.optionalHueResult.isPresent(), true);
        assertEquals(correctResult.optionalHueResult.get().lightOn, "true");

        correctResult = executor.handle(newVoiceRequest("make the light brighter"));
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.optionalHueResult.isPresent(), true);
        assertEquals(correctResult.optionalHueResult.get().brightnessAdjust, HueHandler.BRIGHTNESS_INCREMENT.toString());

        correctResult = executor.handle(newVoiceRequest("brighten the light"));
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.optionalHueResult.isPresent(), true);
        assertEquals(correctResult.optionalHueResult.get().brightnessAdjust, HueHandler.BRIGHTNESS_INCREMENT.toString());

        correctResult = executor.handle(newVoiceRequest("make the light dimmer"));
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.optionalHueResult.isPresent(), true);
        assertEquals(correctResult.optionalHueResult.get().brightnessAdjust, HueHandler.BRIGHTNESS_DECREMENT.toString());

        correctResult = executor.handle(newVoiceRequest("dim the light"));
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.optionalHueResult.isPresent(), true);
        assertEquals(correctResult.optionalHueResult.get().brightnessAdjust, HueHandler.BRIGHTNESS_DECREMENT.toString());

        correctResult = executor.handle(newVoiceRequest("make the light warmer"));
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.optionalHueResult.isPresent(), true);
        assertEquals(correctResult.optionalHueResult.get().colorTempAdjust, HueHandler.COLOR_TEMPERATURE_INCREMENT.toString());

        correctResult = executor.handle(newVoiceRequest("make the light redder"));
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.optionalHueResult.isPresent(), true);
        assertEquals(correctResult.optionalHueResult.get().colorTempAdjust, HueHandler.COLOR_TEMPERATURE_INCREMENT.toString());

        correctResult = executor.handle(newVoiceRequest("make the light cooler"));
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.optionalHueResult.isPresent(), true);
        assertEquals(correctResult.optionalHueResult.get().colorTempAdjust, HueHandler.COLOR_TEMPERATURE_DECREMENT.toString());

        correctResult = executor.handle(newVoiceRequest("make the light bluer"));
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.optionalHueResult.isPresent(), true);
        assertEquals(correctResult.optionalHueResult.get().colorTempAdjust, HueHandler.COLOR_TEMPERATURE_DECREMENT.toString());

        correctResult = executor.handle(newVoiceRequest("Do something random for me"));
        assertNotEquals(HandlerType.HUE, correctResult.handlerType);
    }

    @Test
    public void TestNestHandler() {

        final HandlerExecutor executor = getExecutor();

        HandlerResult correctResult = executor.handle(newVoiceRequest("set the temp to seventy seven degrees"));
        assertEquals(HandlerType.NEST, correctResult.handlerType);
        assertEquals(correctResult.optionalNestResult.isPresent(), true);
        assertEquals(correctResult.optionalNestResult.get().temperatureSet, "77");

        correctResult = executor.handle(newVoiceRequest("set the temp to 77 degrees"));
        assertEquals(HandlerType.NEST, correctResult.handlerType);
        assertEquals(correctResult.optionalNestResult.isPresent(), true);
        assertEquals(correctResult.optionalNestResult.get().temperatureSet, "77");

        correctResult = executor.handle(newVoiceRequest("Do something random for me"));
        assertNotEquals(HandlerType.NEST, correctResult.handlerType);

        correctResult = executor.handle(newVoiceRequest("set the temperature to 75 degrees"));
        assertEquals(HandlerType.NEST, correctResult.handlerType);
        assertEquals(correctResult.optionalNestResult.isPresent(), true);
        assertEquals(correctResult.optionalNestResult.get().temperatureSet, "75");

        // match when "degrees" is not present for numeric pattern
        correctResult = executor.handle(newVoiceRequest("set the temp to 75"));
        assertEquals(HandlerType.NEST, correctResult.handlerType);
        assertEquals(correctResult.optionalNestResult.isPresent(), true);
        assertEquals(correctResult.optionalNestResult.get().temperatureSet, "75");

        correctResult = executor.handle(newVoiceRequest("set the temperature to 75"));
        assertEquals(HandlerType.NEST, correctResult.handlerType);
        assertEquals(correctResult.optionalNestResult.isPresent(), true);
        assertEquals(correctResult.optionalNestResult.get().temperatureSet, "75");

        // no result if missing "degrees" from word pattern
        HandlerResult noResult = executor.handle(newVoiceRequest("set the temperature to seventy-five"));
        assertEquals(HandlerType.NONE, noResult.handlerType);

    }

    @Test
    public void TestRoomConditionHandlerTemperature() {
        final HandlerExecutor executor = getExecutor();

        // testing command text only
        HandlerResult correctResult = executor.handle(newVoiceRequest("what's the current temperature in my room"));
        assertEquals(HandlerType.ROOM_CONDITIONS, correctResult.handlerType);

        correctResult = executor.handle(newVoiceRequest("what is the temperature"));
        assertEquals(HandlerType.ROOM_CONDITIONS, correctResult.handlerType);

        correctResult = executor.handle(newVoiceRequest("what's the temperature"));
        assertEquals(HandlerType.ROOM_CONDITIONS, correctResult.handlerType);

        HandlerResult wrongResult = executor.handle(newVoiceRequest("set the temperature to 75"));
        assertEquals(HandlerType.NEST, wrongResult.handlerType);
    }

    @Test
    public void TestRoomConditionHandlerHumidity() {
        final HandlerExecutor executor = getExecutor();

        HandlerResult correctResult = executor.handle(newVoiceRequest("what is the humidity"));
        assertEquals(HandlerType.ROOM_CONDITIONS, correctResult.handlerType);

        correctResult = executor.handle(newVoiceRequest("what is the current humidity"));
        assertEquals(HandlerType.ROOM_CONDITIONS, correctResult.handlerType);

        correctResult = executor.handle(newVoiceRequest("what's the humidity"));
        assertEquals(HandlerType.ROOM_CONDITIONS, correctResult.handlerType);

        correctResult = executor.handle(newVoiceRequest("what's the humidity in the room"));
        assertEquals(HandlerType.ROOM_CONDITIONS, correctResult.handlerType);

        HandlerResult wrongResult = executor.handle(newVoiceRequest("who is the humidity"));
        assertEquals(HandlerType.NONE, wrongResult.handlerType);
    }

    @Test
    public void TestSleepSoundHandlerPlay() {
        final HandlerExecutor executor = getExecutor();

        HandlerResult correctResult = executor.handle(newVoiceRequest("Play Sleep Sound"));
        assertEquals(HandlerType.SLEEP_SOUNDS, correctResult.handlerType);
        assertEquals(correctResult.command, SpeechCommand.SLEEP_SOUND_PLAY.getValue());

        correctResult = executor.handle(newVoiceRequest("play sleep sounds"));
        assertEquals(HandlerType.SLEEP_SOUNDS, correctResult.handlerType);
        assertEquals(correctResult.command, SpeechCommand.SLEEP_SOUND_PLAY.getValue());

        correctResult = executor.handle(newVoiceRequest("play some sleep sound"));
        assertEquals(HandlerType.SLEEP_SOUNDS, correctResult.handlerType);
        assertEquals(correctResult.command, SpeechCommand.SLEEP_SOUND_PLAY.getValue());

        correctResult = executor.handle(newVoiceRequest("play a sleep sound"));
        assertEquals(HandlerType.SLEEP_SOUNDS, correctResult.handlerType);
        assertEquals(correctResult.command, SpeechCommand.SLEEP_SOUND_PLAY.getValue());

        correctResult = executor.handle(newVoiceRequest("play ambient sound"));
        assertEquals(HandlerType.SLEEP_SOUNDS, correctResult.handlerType);
        assertEquals(correctResult.command, SpeechCommand.SLEEP_SOUND_PLAY.getValue());

        correctResult = executor.handle(newVoiceRequest("play sound"));
        assertEquals(HandlerType.SLEEP_SOUNDS, correctResult.handlerType);
        assertEquals(correctResult.command, SpeechCommand.SLEEP_SOUND_PLAY.getValue());

        correctResult = executor.handle(newVoiceRequest("play rainfall"));
        assertEquals(HandlerType.SLEEP_SOUNDS, correctResult.handlerType);
        assertEquals(correctResult.command, SpeechCommand.SLEEP_SOUND_PLAY.getValue());

        correctResult = executor.handle(newVoiceRequest("begin playing a sleep sound"));
        assertEquals(HandlerType.SLEEP_SOUNDS, correctResult.handlerType);
        assertEquals(correctResult.command, SpeechCommand.SLEEP_SOUND_PLAY.getValue());

        correctResult = executor.handle(newVoiceRequest("play White Noise"));
        assertEquals(HandlerType.SLEEP_SOUNDS, correctResult.handlerType);
        assertEquals(correctResult.command, SpeechCommand.SLEEP_SOUND_PLAY.getValue());

    }

    @Test
    public void TestTimeHandlerPlay() {
        final HandlerExecutor executor = getExecutor();

        HandlerResult correctResult = executor.handle(newVoiceRequest("what is the time"));
        assertEquals(HandlerType.TIME_REPORT, correctResult.handlerType);
        assertEquals(correctResult.command, SpeechCommand.TIME_REPORT.getValue());

        correctResult = executor.handle(newVoiceRequest("what time is it"));
        assertEquals(HandlerType.TIME_REPORT, correctResult.handlerType);
        assertEquals(correctResult.command, SpeechCommand.TIME_REPORT.getValue());

        correctResult = executor.handle(newVoiceRequest("what day is it"));
        assertEquals(HandlerType.TIME_REPORT, correctResult.handlerType);
        assertEquals(correctResult.command, SpeechCommand.DAY_REPORT.getValue());
        final String response = correctResult.responseText();

        correctResult = executor.handle(newVoiceRequest("what's the date"));
        assertEquals(HandlerType.TIME_REPORT, correctResult.handlerType);
        assertEquals(correctResult.command, SpeechCommand.DAY_REPORT.getValue());

        // test might fail if it's run right around midnight!!
        assertEquals(correctResult.responseText(), response);
    }

    @Test
    public void TestBadToken() {
        final HueHandler hueHandler = new HueHandler("sense_dev", speechCommandDAO, badTokenStore, externalApplicationStore, externalAppDataStore, tokenKMSVault);
        final NestHandler nestHandler = new NestHandler(speechCommandDAO, badTokenStore, externalApplicationStore, externalAppDataStore, tokenKMSVault);

        final HandlerExecutor executor = new RegexAnnotationsHandlerExecutor(timeZoneHistoryDAODynamoDB)
            .register(HandlerType.NEST, nestHandler)
            .register(HandlerType.HUE, hueHandler);

        HandlerResult correctResult = executor.handle(newVoiceRequest("turn off the light"));
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.outcome(), Outcome.FAIL);
        assertEquals(correctResult.optionalErrorText().isPresent(), true);
        assertEquals(correctResult.optionalErrorText().get(), "token decrypt failed");

        correctResult = executor.handle(newVoiceRequest("set the temp to seventy seven degrees"));
        assertEquals(HandlerType.NEST, correctResult.handlerType);
        assertEquals(correctResult.outcome(), Outcome.FAIL);
        assertEquals(correctResult.optionalErrorText().isPresent(), true);
        assertEquals(correctResult.optionalErrorText().get(), "token decrypt failed");
    }
}
