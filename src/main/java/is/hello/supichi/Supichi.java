package is.hello.supichi;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.kinesis.producer.KinesisProducer;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.hello.suripu.app.configuration.SuripuAppConfiguration;
import com.hello.suripu.app.sensors.ScaleFactory;
import com.hello.suripu.app.sensors.SensorViewFactory;
import com.hello.suripu.app.sensors.SensorViewLogic;
import com.hello.suripu.core.configuration.DynamoDBTableName;
import com.hello.suripu.core.db.AccountLocationDAO;
import com.hello.suripu.core.db.AlarmDAODynamoDB;
import com.hello.suripu.core.db.CalibrationDAO;
import com.hello.suripu.core.db.CalibrationDynamoDB;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.db.DeviceDataDAODynamoDB;
import com.hello.suripu.core.db.FileInfoDAO;
import com.hello.suripu.core.db.FileManifestDAO;
import com.hello.suripu.core.db.FileManifestDynamoDB;
import com.hello.suripu.core.db.KeyStore;
import com.hello.suripu.core.db.KeyStoreDynamoDB;
import com.hello.suripu.core.db.MergedUserInfoDynamoDB;
import com.hello.suripu.core.db.SleepStatsDAODynamoDB;
import com.hello.suripu.core.db.TimeZoneHistoryDAODynamoDB;
import com.hello.suripu.core.db.colors.SenseColorDAO;
import com.hello.suripu.core.db.colors.SenseColorDAOSQLImpl;
import com.hello.suripu.core.models.device.v2.DeviceProcessor;
import com.hello.suripu.core.preferences.AccountPreferencesDAO;
import com.hello.suripu.core.preferences.AccountPreferencesDynamoDB;
import com.hello.suripu.core.processors.SleepSoundsProcessor;
import com.hello.suripu.core.speech.interfaces.Vault;
import com.hello.suripu.coredropwizard.clients.AmazonDynamoDBClientFactory;
import com.hello.suripu.coredropwizard.clients.MessejiClient;
import com.hello.suripu.coredropwizard.timeline.InstrumentedTimelineProcessor;
import com.ibm.watson.developer_cloud.text_to_speech.v1.TextToSpeech;
import com.maxmind.geoip2.DatabaseReader;
import io.dropwizard.setup.Environment;
import io.dropwizard.util.Duration;
import is.hello.gaibu.core.db.ExpansionDataDAO;
import is.hello.gaibu.core.db.ExpansionsDAO;
import is.hello.gaibu.core.db.ExternalTokenDAO;
import is.hello.gaibu.core.stores.PersistentExpansionDataStore;
import is.hello.gaibu.core.stores.PersistentExpansionStore;
import is.hello.gaibu.core.stores.PersistentExternalTokenStore;
import is.hello.supichi.api.Speech;
import is.hello.supichi.clients.InstrumentedSpeechClient;
import is.hello.supichi.clients.SpeechClientManaged;
import is.hello.supichi.commandhandlers.HandlerFactory;
import is.hello.supichi.configuration.KinesisProducerConfiguration;
import is.hello.supichi.configuration.KinesisStream;
import is.hello.supichi.configuration.SpeechConfiguration;
import is.hello.supichi.configuration.WatsonConfiguration;
import is.hello.supichi.db.SpeechCommandDynamoDB;
import is.hello.supichi.executors.HandlerExecutor;
import is.hello.supichi.executors.RegexAnnotationsHandlerExecutor;
import is.hello.supichi.handler.AudioRequestHandler;
import is.hello.supichi.handler.SignedBodyHandler;
import is.hello.supichi.kinesis.KinesisData;
import is.hello.supichi.kinesis.SpeechKinesisProducer;
import is.hello.supichi.models.HandlerType;
import is.hello.supichi.resources.demo.DemoUploadResource;
import is.hello.supichi.resources.ping.PingResource;
import is.hello.supichi.resources.v2.UploadResource;
import is.hello.supichi.response.CachedResponseBuilder;
import is.hello.supichi.response.SilentResponseBuilder;
import is.hello.supichi.response.StaticResponseBuilder;
import is.hello.supichi.response.SupichiResponseBuilder;
import is.hello.supichi.response.SupichiResponseType;
import is.hello.supichi.response.WatsonResponseBuilder;
import is.hello.supichi.utils.GeoUtils;
import net.spy.memcached.AddrUtil;
import net.spy.memcached.ConnectionFactoryBuilder;
import net.spy.memcached.MemcachedClient;
import org.skife.jdbi.v2.DBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Created by ksg on 10/18/16
 */
public class Supichi
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Supichi.class);

    private final AudioRequestHandler audioRequestHandler;

    public Supichi(
            final Environment environment,
            final SuripuAppConfiguration configuration,
            final AmazonDynamoDBClientFactory dynamoDBClientFactory,
            final ImmutableMap<DynamoDBTableName, String> tableNames,
            final DBI commonDB,
            final InstrumentedTimelineProcessor timelineProcessor,
            final MessejiClient messejiClient,
            final Vault tokenKMSVault,
            final DeviceProcessor deviceProcessor) throws IOException {

        final AWSCredentialsProvider awsCredentialsProvider = new DefaultAWSCredentialsProviderChain();
        final ClientConfiguration clientConfiguration = new ClientConfiguration();
        clientConfiguration.withConnectionTimeout(200); // in ms
        clientConfiguration.withMaxErrorRetry(1);

        final AmazonS3 amazonS3 = new AmazonS3Client(awsCredentialsProvider, clientConfiguration);
        amazonS3.setRegion(Region.getRegion(Regions.US_EAST_1));
        amazonS3.setEndpoint(configuration.s3Endpoint());

        final SpeechConfiguration speechConfiguration = configuration.speechConfiguration();

        // set up all the DAOs
        final AmazonDynamoDB speechCommandClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.SPEECH_COMMANDS);
        final SpeechCommandDynamoDB speechCommandDAO = new SpeechCommandDynamoDB(speechCommandClient, tableNames.get(DynamoDBTableName.SPEECH_COMMANDS));

        final AmazonDynamoDB senseKeyStoreDynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.SENSE_KEY_STORE);
        final KeyStore senseKeyStore = new KeyStoreDynamoDB(
                senseKeyStoreDynamoDBClient,
                tableNames.get(DynamoDBTableName.SENSE_KEY_STORE),
                "1234567891234567".getBytes(), // TODO: REMOVE THIS WHEN WE ARE NOT SUPPOSED TO HAVE A DEFAULT KEY
                120 // 2 minutes for cache
        );

        final AmazonDynamoDB fileManifestDynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.FILE_MANIFEST);
        final FileManifestDAO fileManifestDAO = new FileManifestDynamoDB(fileManifestDynamoDBClient, tableNames.get(DynamoDBTableName.FILE_MANIFEST));

        final AmazonDynamoDB deviceDataDAODynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.DEVICE_DATA);
        final DeviceDataDAODynamoDB deviceDataDAODynamoDB = new DeviceDataDAODynamoDB(deviceDataDAODynamoDBClient, tableNames.get(DynamoDBTableName.DEVICE_DATA));

        final AmazonDynamoDB calibrationDynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.CALIBRATION);
        final CalibrationDAO calibrationDAO = CalibrationDynamoDB.create(calibrationDynamoDBClient, tableNames.get(DynamoDBTableName.CALIBRATION));

        final AmazonDynamoDB timezoneHistoryDynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.TIMEZONE_HISTORY);
        final TimeZoneHistoryDAODynamoDB timeZoneHistoryDAODynamoDB = new TimeZoneHistoryDAODynamoDB(timezoneHistoryDynamoDBClient, tableNames.get(DynamoDBTableName.TIMEZONE_HISTORY));

        final AmazonDynamoDB mergedUserInfoDynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.ALARM_INFO);
        final MergedUserInfoDynamoDB mergedUserInfoDynamoDB = new MergedUserInfoDynamoDB(mergedUserInfoDynamoDBClient, tableNames.get(DynamoDBTableName.ALARM_INFO));

        final AmazonDynamoDB alarmDynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.ALARM);
        final AlarmDAODynamoDB alarmDAODynamoDB = new AlarmDAODynamoDB(alarmDynamoDBClient, tableNames.get(DynamoDBTableName.ALARM));

        final AmazonDynamoDB dynamoDBStatsClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.SLEEP_STATS);
        final SleepStatsDAODynamoDB sleepStatsDAODynamoDB = new SleepStatsDAODynamoDB(dynamoDBStatsClient, tableNames.get(DynamoDBTableName.SLEEP_STATS), configuration.getSleepStatsVersion());

        final AmazonDynamoDB prefsClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.PREFERENCES);
        final AccountPreferencesDAO accountPreferencesDAO = AccountPreferencesDynamoDB.create(prefsClient, tableNames.get(DynamoDBTableName.PREFERENCES));

        final ExpansionsDAO expansionsDAO = commonDB.onDemand(ExpansionsDAO.class);
        final PersistentExpansionStore expansionStore = new PersistentExpansionStore(expansionsDAO);

        final ExternalTokenDAO externalTokenDAO = commonDB.onDemand(ExternalTokenDAO.class);
        final PersistentExternalTokenStore externalTokenStore = new PersistentExternalTokenStore(externalTokenDAO, expansionStore);

        final ExpansionDataDAO expansionsDataDAO = commonDB.onDemand(ExpansionDataDAO.class);
        final PersistentExpansionDataStore expansionsDataStore = new PersistentExpansionDataStore(expansionsDataDAO);

        final FileInfoDAO fileInfoDAO = commonDB.onDemand(FileInfoDAO.class);
        final DeviceDAO deviceDAO = commonDB.onDemand(DeviceDAO.class);
        final SenseColorDAO senseColorDAO = commonDB.onDemand(SenseColorDAOSQLImpl.class);
        final AccountLocationDAO accountLocationDAO = commonDB.onDemand(AccountLocationDAO.class);

        final SleepSoundsProcessor sleepSoundsProcessor = SleepSoundsProcessor.create(fileInfoDAO, fileManifestDAO);

        final SensorViewFactory sensorViewFactory = SensorViewFactory.build(new ScaleFactory());
        final SensorViewLogic sensorViewLogic = new SensorViewLogic(deviceDataDAODynamoDB, senseKeyStore, deviceDAO, senseColorDAO, calibrationDAO, sensorViewFactory);

        // set up speech client
        final InstrumentedSpeechClient client;
        try {
            client = new InstrumentedSpeechClient(
                    speechConfiguration.googleAPIHost(),
                    speechConfiguration.googleAPIPort(),
                    speechConfiguration.audioConfiguration(),
                    environment.metrics());
        } catch (IOException e) {
            LOGGER.error("error=fail-to-create-google-speech-client error_msg={}", e.getMessage());
            throw new RuntimeException("Fail to create google speech client");
        }

        final SpeechClientManaged speechClientManaged = new SpeechClientManaged(client);
        environment.lifecycle().manage(speechClientManaged);

        Optional<DatabaseReader> geoIPDatabase = GeoUtils.geoIPDatabase();

        // set up command-handlers
        final HandlerFactory handlerFactory = HandlerFactory.create(
                speechCommandDAO,
                messejiClient,
                sleepSoundsProcessor,
                timeZoneHistoryDAODynamoDB,
                speechConfiguration.forecastio(),
                accountLocationDAO,
                externalTokenStore,
                expansionStore,
                expansionsDataStore,
                tokenKMSVault,
                alarmDAODynamoDB,
                mergedUserInfoDynamoDB,
                sleepStatsDAODynamoDB,
                timelineProcessor,
                geoIPDatabase,
                sensorViewLogic,
                accountPreferencesDAO
        );

        final HandlerExecutor handlerExecutor = new RegexAnnotationsHandlerExecutor(timeZoneHistoryDAODynamoDB) //new RegexHandlerExecutor()
                .register(HandlerType.ALARM, handlerFactory.alarmHandler())
                .register(HandlerType.WEATHER, handlerFactory.weatherHandler())
                .register(HandlerType.SLEEP_SOUNDS, handlerFactory.sleepSoundHandler())
                .register(HandlerType.ROOM_CONDITIONS, handlerFactory.roomConditionsHandler())
                .register(HandlerType.TIME_REPORT, handlerFactory.timeHandler())
                .register(HandlerType.TRIVIA, handlerFactory.triviaHandler())
                .register(HandlerType.TIMELINE, handlerFactory.timelineHandler())
                .register(HandlerType.HUE, handlerFactory.hueHandler(configuration.expansionConfiguration().hueAppName()))
                .register(HandlerType.NEST, handlerFactory.nestHandler())
                .register(HandlerType.SLEEP_SUMMARY, handlerFactory.sleepSummaryHandler());

        // set up Kinesis Producer
        final KinesisStream kinesisStream = KinesisStream.SPEECH_RESULT;

        final KinesisProducerConfiguration kinesisProducerConfiguration = speechConfiguration.kinesisProducerConfiguration();
        final com.amazonaws.services.kinesis.producer.KinesisProducerConfiguration kplConfig = new com.amazonaws.services.kinesis.producer.KinesisProducerConfiguration()
                .setRegion(kinesisProducerConfiguration.region())
                .setCredentialsProvider(awsCredentialsProvider)
                .setMaxConnections(kinesisProducerConfiguration.maxConnections())
                .setRequestTimeout(kinesisProducerConfiguration.requstTimeout())
                .setRecordMaxBufferedTime(kinesisProducerConfiguration.recordMaxBufferedTime())
                .setCredentialsRefreshDelay(1000L);

        final ExecutorService kinesisExecutor = environment.lifecycle().executorService("kinesis_producer")
                .minThreads(1)
                .maxThreads(2)
                .keepAliveTime(Duration.seconds(2L)).build();

        final ScheduledExecutorService kinesisMetricsExecutor = environment.lifecycle().scheduledExecutorService("kinesis_producer_metrics").threads(1).build();

        final KinesisProducer kinesisProducer = new KinesisProducer(kplConfig);
        final String kinesisStreamName = kinesisProducerConfiguration.streams().get(kinesisStream);
        final BlockingQueue<KinesisData> kinesisEvents = new ArrayBlockingQueue<>(kinesisProducerConfiguration.queueSize());
        final Boolean showUUIDInLogs = configuration.getDebug();
        final SpeechKinesisProducer speechKinesisProducer = new SpeechKinesisProducer(kinesisStreamName, kinesisEvents, kinesisProducer, kinesisExecutor, kinesisMetricsExecutor, showUUIDInLogs);

        environment.lifecycle().manage(speechKinesisProducer);

        // set up watson
        final TextToSpeech watson = new TextToSpeech();
        final WatsonConfiguration watsonConfiguration = speechConfiguration.watsonConfiguration();
        watson.setUsernameAndPassword(watsonConfiguration.getUsername(), watsonConfiguration.getPassword());
        final Map<String, String> headers = ImmutableMap.of("X-Watson-Learning-Opt-Out", "true");
        watson.setDefaultHeaders(headers);

        // Map Eq profile to s3 bucket/path
        final String speechBucket = speechConfiguration.watsonAudioConfiguration().getBucketName();
        final String s3ResponseBucket = String.format("%s/%s", speechBucket, speechConfiguration.watsonAudioConfiguration().getAudioPrefix());
        final String s3ResponseBucketNoEq = String.format("%s/voice/watson-text2speech/16k", speechBucket);
        final Map<Speech.Equalizer, String> eqMap = ImmutableMap.<Speech.Equalizer, String>builder()
                .put(Speech.Equalizer.SENSE_ONE, s3ResponseBucket)
                .put(Speech.Equalizer.NONE, s3ResponseBucketNoEq)
                .build();

        final StaticResponseBuilder staticResponseBuilder = StaticResponseBuilder.create();
        final WatsonResponseBuilder watsonResponseBuilder = new WatsonResponseBuilder(watson, watsonConfiguration.getVoiceName(), environment.metrics());
        final Map<SupichiResponseType, SupichiResponseBuilder> responseBuilders = Maps.newHashMap();
        responseBuilders.put(SupichiResponseType.STATIC, staticResponseBuilder);
        responseBuilders.put(SupichiResponseType.WATSON, watsonResponseBuilder);
        responseBuilders.put(SupichiResponseType.SILENT, new SilentResponseBuilder());

        final List<String> memcacheHosts = configuration.speechConfiguration().memcacheHosts();
        if (!memcacheHosts.isEmpty()) {
            MemcachedClient mc;
            try {
                mc = new MemcachedClient(
                        new ConnectionFactoryBuilder()
                                .setOpTimeout(500) // 500ms
                                .setProtocol(ConnectionFactoryBuilder.Protocol.BINARY)
                                .build(),
                        AddrUtil.getAddresses(memcacheHosts));
            } catch (IOException io) {
                LOGGER.error("error=memcache-connection-failed message={}", io.getMessage());
                throw new RuntimeException(io.getMessage());
            }

            final String cachePrefix = configuration.speechConfiguration().cachePrefix();
            final CachedResponseBuilder cachedResponseBuilder = new CachedResponseBuilder(watsonConfiguration.getVoiceName(), watsonResponseBuilder, mc, cachePrefix);
            // Override watson
            responseBuilders.put(SupichiResponseType.WATSON, cachedResponseBuilder);
        }

        // map command-handlers to response-builders
        final Map<HandlerType, SupichiResponseType> handlersToBuilders = handlerExecutor.responseBuilders();

        final SignedBodyHandler signedBodyHandler = new SignedBodyHandler(senseKeyStore);


        this.audioRequestHandler = new AudioRequestHandler(
                client, signedBodyHandler, handlerExecutor, deviceDAO,
                speechKinesisProducer, responseBuilders, handlersToBuilders,
                deviceProcessor,
                environment.metrics());

    }

    public UploadResource uploadResource() {
        return new UploadResource(audioRequestHandler);
    }

    public DemoUploadResource demoUploadResource() {
        return new DemoUploadResource(audioRequestHandler);
    }

    public PingResource pingResource() {
        return new PingResource();
    }
}
