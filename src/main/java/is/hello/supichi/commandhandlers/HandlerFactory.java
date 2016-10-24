package is.hello.supichi.commandhandlers;

import com.google.common.base.Optional;
import com.hello.suripu.core.alarm.AlarmProcessor;
import com.hello.suripu.core.db.AccountLocationDAO;
import com.hello.suripu.core.db.AlarmDAODynamoDB;
import com.hello.suripu.core.db.CalibrationDAO;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.db.DeviceDataDAODynamoDB;
import com.hello.suripu.core.db.MergedUserInfoDynamoDB;
import com.hello.suripu.core.db.SleepStatsDAODynamoDB;
import com.hello.suripu.core.db.TimeZoneHistoryDAODynamoDB;
import com.hello.suripu.core.db.colors.SenseColorDAO;
import com.hello.suripu.core.processors.SleepSoundsProcessor;
import com.hello.suripu.core.speech.interfaces.Vault;
import com.hello.suripu.coredropwizard.clients.MessejiClient;
import com.hello.suripu.coredropwizard.timeline.InstrumentedTimelineProcessor;
import com.maxmind.geoip2.DatabaseReader;
import is.hello.gaibu.core.stores.PersistentExpansionDataStore;
import is.hello.gaibu.core.stores.PersistentExpansionStore;
import is.hello.gaibu.core.stores.PersistentExternalTokenStore;
import is.hello.gaibu.weather.clients.DarkSky;
import is.hello.gaibu.weather.interfaces.WeatherReport;
import is.hello.supichi.db.SpeechCommandDAO;

/**
 * Created by ksg on 6/17/16
 */
public class HandlerFactory {

    private final SpeechCommandDAO speechCommandDAO;
    private final MessejiClient messejiClient;
    private final SleepSoundsProcessor sleepSoundsProcessor;
    private final DeviceDataDAODynamoDB deviceDataDAODynamoDB;
    private final DeviceDAO deviceDAO;
    private final SenseColorDAO senseColorDAO;
    private final CalibrationDAO calibrationDAO;
    private final TimeZoneHistoryDAODynamoDB timeZoneHistoryDAODynamoDB;
    private final String forecastio;
    private final AccountLocationDAO accountLocationDAO;
    private final PersistentExternalTokenStore externalTokenStore;
    private final Vault tokenKMSVault;
    private final PersistentExpansionStore externalApplicationStore;
    private final PersistentExpansionDataStore externalAppDataStore;
    private final AlarmDAODynamoDB alarmDAODynamoDB;
    private final MergedUserInfoDynamoDB mergedUserInfoDynamoDB;
    private final SleepStatsDAODynamoDB sleepStatsDAO;

    private final InstrumentedTimelineProcessor timelineProcessor;
    private final Optional<DatabaseReader> geoIpDatabase;

    private HandlerFactory(final SpeechCommandDAO speechCommandDAO,
                           final MessejiClient messejiClient,
                           final SleepSoundsProcessor sleepSoundsProcessor,
                           final DeviceDataDAODynamoDB deviceDataDAODynamoDB,
                           final DeviceDAO deviceDAO,
                           final SenseColorDAO senseColorDAO,
                           final CalibrationDAO calibrationDAO,
                           final TimeZoneHistoryDAODynamoDB timeZoneHistoryDAODynamoDB,
                           final String forecastio,
                           final AccountLocationDAO accountLocationDAO,
                           final PersistentExternalTokenStore externalTokenStore,
                           final PersistentExpansionStore expansionStore,
                           final PersistentExpansionDataStore expansionDataStore,
                           final Vault tokenKMSVault,
                           final AlarmDAODynamoDB alarmDAODynamoDB,
                           final MergedUserInfoDynamoDB mergedUserInfoDynamoDB,
                           final SleepStatsDAODynamoDB sleepStatsDAO,
                           final InstrumentedTimelineProcessor timelineProcessor,
                           final Optional<DatabaseReader> geoIpDatabase) {
        this.speechCommandDAO = speechCommandDAO;
        this.messejiClient = messejiClient;
        this.sleepSoundsProcessor = sleepSoundsProcessor;
        this.deviceDataDAODynamoDB = deviceDataDAODynamoDB;
        this.deviceDAO = deviceDAO;
        this.senseColorDAO = senseColorDAO;
        this.calibrationDAO = calibrationDAO;
        this.timeZoneHistoryDAODynamoDB = timeZoneHistoryDAODynamoDB;
        this.forecastio = forecastio;
        this.accountLocationDAO = accountLocationDAO;
        this.externalTokenStore = externalTokenStore;
        this.externalApplicationStore = expansionStore;
        this.externalAppDataStore = expansionDataStore;
        this.tokenKMSVault = tokenKMSVault;
        this.alarmDAODynamoDB = alarmDAODynamoDB;
        this.mergedUserInfoDynamoDB = mergedUserInfoDynamoDB;
        this.sleepStatsDAO = sleepStatsDAO;
        this.timelineProcessor = timelineProcessor;
        this.geoIpDatabase = geoIpDatabase;
    }

    public static HandlerFactory create(final SpeechCommandDAO speechCommandDAO,
                                        final MessejiClient messejiClient,
                                        final SleepSoundsProcessor sleepSoundsProcessor,
                                        final DeviceDataDAODynamoDB deviceDataDAODynamoDB,
                                        final DeviceDAO deviceDAO,
                                        final SenseColorDAO senseColorDAO,
                                        final CalibrationDAO calibrationDAO,
                                        final TimeZoneHistoryDAODynamoDB timeZoneHistoryDAODynamoDB,
                                        final String forecastio,
                                        final AccountLocationDAO accountLocationDAO,
                                        final PersistentExternalTokenStore externalTokenStore,
                                        final PersistentExpansionStore expansionStore,
                                        final PersistentExpansionDataStore expansionDataStore,
                                        final Vault tokenKMSVault,
                                        final AlarmDAODynamoDB alarmDAODynamoDB,
                                        final MergedUserInfoDynamoDB mergedUserInfoDynamoDB,
                                        final SleepStatsDAODynamoDB sleepStatsDAO,
                                        final InstrumentedTimelineProcessor timelineProcessor,
                                        final Optional<DatabaseReader> geoIPDatabase
                                        ) {

        return new HandlerFactory(speechCommandDAO, messejiClient, sleepSoundsProcessor, deviceDataDAODynamoDB,
                deviceDAO, senseColorDAO, calibrationDAO,timeZoneHistoryDAODynamoDB, forecastio, accountLocationDAO,
            externalTokenStore, expansionStore, expansionDataStore, tokenKMSVault,
                alarmDAODynamoDB, mergedUserInfoDynamoDB, sleepStatsDAO,
                timelineProcessor, geoIPDatabase);
    }

    public WeatherHandler weatherHandler() {
        if (geoIpDatabase.isPresent()) {
            final WeatherReport weatherReport = DarkSky.create(forecastio, geoIpDatabase.get());
            return WeatherHandler.create(speechCommandDAO, weatherReport, accountLocationDAO);
        }
        throw new RuntimeException("Issues fetching geoip db. Bailing");
    }

    public AlarmHandler alarmHandler() {
        final AlarmProcessor alarmProcessor = new AlarmProcessor(alarmDAODynamoDB, mergedUserInfoDynamoDB);
        return new AlarmHandler(speechCommandDAO, alarmProcessor, mergedUserInfoDynamoDB);
    }

    public TimeHandler timeHandler() {
        return new TimeHandler(speechCommandDAO, timeZoneHistoryDAODynamoDB, geoIpDatabase);
    }

    public TriviaHandler triviaHandler() {
        return new TriviaHandler(speechCommandDAO);
    }

    public TimelineHandler timelineHandler() {
        return new TimelineHandler(speechCommandDAO);
    }

    public RoomConditionsHandler roomConditionsHandler() {
        return new RoomConditionsHandler(speechCommandDAO, deviceDataDAODynamoDB,deviceDAO,senseColorDAO,calibrationDAO);
    }

    public SleepSoundHandler sleepSoundHandler() {
        return new SleepSoundHandler(messejiClient, speechCommandDAO, sleepSoundsProcessor, 5);
    }

    public HueHandler hueHandler(final String appName) {
        return new HueHandler(appName, speechCommandDAO, externalTokenStore, externalApplicationStore, externalAppDataStore, tokenKMSVault);
    }

    public NestHandler nestHandler() {
        return new NestHandler(speechCommandDAO, externalTokenStore, externalApplicationStore, externalAppDataStore, tokenKMSVault);
    }

    public AlexaHandler alexaHandler() {
        return new AlexaHandler(speechCommandDAO);
    }

    public SleepSummaryHandler sleepSummaryHandler() {
        return new SleepSummaryHandler(speechCommandDAO, sleepStatsDAO, timelineProcessor);
    }
}