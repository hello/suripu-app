package is.hello.speech;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.kinesis.producer.KinesisProducer;
import com.amazonaws.services.s3.AmazonS3;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.hello.suripu.core.db.AccountLocationDAO;
import com.hello.suripu.core.db.AlarmDAODynamoDB;
import com.hello.suripu.core.db.CalibrationDAO;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.db.DeviceDataDAODynamoDB;
import com.hello.suripu.core.db.KeyStore;
import com.hello.suripu.core.db.MergedUserInfoDynamoDB;
import com.hello.suripu.core.db.SleepStatsDAODynamoDB;
import com.hello.suripu.core.db.TimeZoneHistoryDAODynamoDB;
import com.hello.suripu.core.db.colors.SenseColorDAO;
import com.hello.suripu.core.processors.SleepSoundsProcessor;
import com.hello.suripu.core.speech.interfaces.Vault;
import com.hello.suripu.coredropwizard.clients.MessejiClient;
import com.hello.suripu.coredropwizard.timeline.InstrumentedTimelineProcessor;
import com.ibm.watson.developer_cloud.text_to_speech.v1.TextToSpeech;
import com.maxmind.geoip2.DatabaseReader;
import io.dropwizard.setup.Environment;
import io.dropwizard.util.Duration;
import is.hello.gaibu.core.stores.PersistentExpansionDataStore;
import is.hello.gaibu.core.stores.PersistentExpansionStore;
import is.hello.gaibu.core.stores.PersistentExternalTokenStore;
import is.hello.speech.api.Speech;
import is.hello.speech.clients.SpeechClient;
import is.hello.speech.clients.SpeechClientManaged;
import is.hello.speech.commandhandlers.HandlerFactory;
import is.hello.speech.configuration.KinesisProducerConfiguration;
import is.hello.speech.configuration.KinesisStream;
import is.hello.speech.configuration.SpeechConfiguration;
import is.hello.speech.configuration.WatsonConfiguration;
import is.hello.speech.db.SpeechCommandDAO;
import is.hello.speech.executors.HandlerExecutor;
import is.hello.speech.executors.RegexAnnotationsHandlerExecutor;
import is.hello.speech.handler.AudioRequestHandler;
import is.hello.speech.handler.SignedBodyHandler;
import is.hello.speech.kinesis.KinesisData;
import is.hello.speech.kinesis.SpeechKinesisProducer;
import is.hello.speech.models.HandlerType;
import is.hello.speech.resources.demo.DemoUploadResource;
import is.hello.speech.resources.v2.UploadResource;
import is.hello.speech.response.S3ResponseBuilder;
import is.hello.speech.response.SupichiResponseBuilder;
import is.hello.speech.response.SupichiResponseType;
import is.hello.speech.response.WatsonResponseBuilder;
import is.hello.speech.utils.GeoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
            final SpeechConfiguration speechConfiguration,
            final KeyStore senseKeyStore,
            final SpeechCommandDAO speechCommandDAO,
            final MessejiClient messejiClient,
            final SleepSoundsProcessor sleepSoundsProcessor,
            final DeviceDataDAODynamoDB deviceDataDAODynamoDB,
            final DeviceDAO deviceDAO,
            final SenseColorDAO senseColorDAO,
            final CalibrationDAO calibrationDAO,
            final TimeZoneHistoryDAODynamoDB timeZoneHistoryDAODynamoDB,
            final AccountLocationDAO accountLocationDAO,
            final PersistentExternalTokenStore externalTokenStore,
            final PersistentExpansionStore expansionStore,
            final PersistentExpansionDataStore externalAppDataStore,
            final Vault tokenKMSVault,
            final AlarmDAODynamoDB alarmDAODynamoDB,
            final MergedUserInfoDynamoDB mergedUserInfoDynamoDB,
            final SleepStatsDAODynamoDB sleepStatsDAODynamoDB,
            final InstrumentedTimelineProcessor timelineProcessor,
            final String hueName,
            final AWSCredentialsProvider awsCredentialsProvider,
            final AmazonS3 amazonS3) {

        // set up speech client
        final SpeechClient client;
        try {
            client = new SpeechClient(
                    speechConfiguration.googleAPIHost(),
                    speechConfiguration.googleAPIPort(),
                    speechConfiguration.audioConfiguration());
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
                deviceDataDAODynamoDB,
                deviceDAO,
                senseColorDAO,
                calibrationDAO,
                timeZoneHistoryDAODynamoDB,
                speechConfiguration.forecastio(),
                accountLocationDAO,
                externalTokenStore,
                expansionStore,
                externalAppDataStore,
                tokenKMSVault,
                alarmDAODynamoDB,
                mergedUserInfoDynamoDB,
                sleepStatsDAODynamoDB,
                timelineProcessor,
                geoIPDatabase
        );

        final HandlerExecutor handlerExecutor = new RegexAnnotationsHandlerExecutor(timeZoneHistoryDAODynamoDB) //new RegexHandlerExecutor()
                .register(HandlerType.ALARM, handlerFactory.alarmHandler())
                .register(HandlerType.WEATHER, handlerFactory.weatherHandler())
                .register(HandlerType.SLEEP_SOUNDS, handlerFactory.sleepSoundHandler())
                .register(HandlerType.ROOM_CONDITIONS, handlerFactory.roomConditionsHandler())
                .register(HandlerType.TIME_REPORT, handlerFactory.timeHandler())
                .register(HandlerType.TRIVIA, handlerFactory.triviaHandler())
                .register(HandlerType.TIMELINE, handlerFactory.timelineHandler())
                .register(HandlerType.HUE, handlerFactory.hueHandler(hueName))
                .register(HandlerType.NEST, handlerFactory.nestHandler())
                .register(HandlerType.ALEXA, handlerFactory.alexaHandler())
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
        final SpeechKinesisProducer speechKinesisProducer = new SpeechKinesisProducer(kinesisStreamName, kinesisEvents, kinesisProducer, kinesisExecutor, kinesisMetricsExecutor);

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

        // set up response-builders
        final S3ResponseBuilder s3ResponseBuilder = new S3ResponseBuilder(amazonS3, eqMap, "WATSON", watsonConfiguration.getVoiceName());
        final WatsonResponseBuilder watsonResponseBuilder = new WatsonResponseBuilder(watson, watsonConfiguration.getVoiceName());

        final Map<SupichiResponseType, SupichiResponseBuilder> responseBuilders = Maps.newHashMap();

        responseBuilders.put(SupichiResponseType.S3, s3ResponseBuilder);
        responseBuilders.put(SupichiResponseType.WATSON, watsonResponseBuilder);

        // map command-handlers to response-builders
        final Map<HandlerType, SupichiResponseType> handlersToBuilders = handlerExecutor.responseBuilders();

        final SignedBodyHandler signedBodyHandler = new SignedBodyHandler(senseKeyStore);


        this.audioRequestHandler = new AudioRequestHandler(
                client, signedBodyHandler, handlerExecutor, deviceDAO,
                speechKinesisProducer, responseBuilders, handlersToBuilders,
                environment.metrics());

    }

    public UploadResource uploadResource() {
        return new UploadResource(audioRequestHandler);
    }

    public DemoUploadResource demoUploadResource() {
        return new DemoUploadResource(audioRequestHandler);
    }
}
