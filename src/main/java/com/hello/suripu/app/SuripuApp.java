package com.hello.suripu.app;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClient;
import com.amazonaws.services.kinesis.AmazonKinesisAsyncClient;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.google.common.collect.ImmutableMap;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hello.dropwizard.mikkusu.resources.PingResource;
import com.hello.dropwizard.mikkusu.resources.VersionResource;
import com.hello.suripu.app.cli.CreateDynamoDBTables;
import com.hello.suripu.app.cli.MigrateDeviceDataCommand;
import com.hello.suripu.app.cli.MigratePillHeartbeatCommand;
import com.hello.suripu.app.cli.MovePillDataToDynamoDBCommand;
import com.hello.suripu.app.cli.RecreatePillColorCommand;
import com.hello.suripu.app.clients.TaimurainHttpClient;
import com.hello.suripu.app.configuration.MessejiHttpClientConfiguration;
import com.hello.suripu.app.configuration.SuripuAppConfiguration;
import com.hello.suripu.app.messeji.MessejiClient;
import com.hello.suripu.app.messeji.MessejiHttpClient;
import com.hello.suripu.app.modules.RolloutAppModule;
import com.hello.suripu.app.resources.v1.AccountPreferencesResource;
import com.hello.suripu.app.resources.v1.AccountResource;
import com.hello.suripu.app.resources.v1.AlarmResource;
import com.hello.suripu.app.resources.v1.AppCheckinResource;
import com.hello.suripu.app.resources.v1.AppStatsResource;
import com.hello.suripu.app.resources.v1.DeviceResources;
import com.hello.suripu.app.resources.v1.FeedbackResource;
import com.hello.suripu.app.resources.v1.InsightsResource;
import com.hello.suripu.app.resources.v1.MobilePushRegistrationResource;
import com.hello.suripu.app.resources.v1.OAuthResource;
import com.hello.suripu.app.resources.v1.PasswordResetResource;
import com.hello.suripu.app.resources.v1.ProvisionResource;
import com.hello.suripu.app.resources.v1.QuestionsResource;
import com.hello.suripu.app.resources.v1.RoomConditionsResource;
import com.hello.suripu.app.resources.v1.SupportResource;
import com.hello.suripu.app.resources.v1.TimeZoneResource;
import com.hello.suripu.app.resources.v1.TimelineResource;
import com.hello.suripu.app.v2.DeviceResource;
import com.hello.suripu.app.v2.SleepSoundsResource;
import com.hello.suripu.app.v2.StoreFeedbackResource;
import com.hello.suripu.app.v2.TrendsResource;
import com.hello.suripu.core.ObjectGraphRoot;
import com.hello.suripu.core.configuration.DynamoDBTableName;
import com.hello.suripu.core.configuration.QueueName;

import com.hello.suripu.core.db.AccountDAO;
import com.hello.suripu.core.db.AccountDAOImpl;
import com.hello.suripu.core.db.AccountLocationDAO;
import com.hello.suripu.core.db.AlarmDAODynamoDB;
import com.hello.suripu.core.db.AppStatsDAO;
import com.hello.suripu.core.db.AppStatsDAODynamoDB;
import com.hello.suripu.core.db.ApplicationsDAO;
import com.hello.suripu.core.db.CalibrationDAO;
import com.hello.suripu.core.db.CalibrationDynamoDB;
import com.hello.suripu.core.db.DefaultModelEnsembleDAO;
import com.hello.suripu.core.db.DefaultModelEnsembleFromS3;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.db.DeviceDataDAODynamoDB;
import com.hello.suripu.core.db.FeatureExtractionModelsDAO;
import com.hello.suripu.core.db.FeatureExtractionModelsDAODynamoDB;
import com.hello.suripu.core.db.FeatureStore;
import com.hello.suripu.core.db.FeedbackDAO;
import com.hello.suripu.core.db.FileInfoDAO;
import com.hello.suripu.core.db.FileManifestDAO;
import com.hello.suripu.core.db.FileManifestDynamoDB;
import com.hello.suripu.core.db.InsightsDAODynamoDB;
import com.hello.suripu.core.db.KeyStore;
import com.hello.suripu.core.db.KeyStoreDynamoDB;
import com.hello.suripu.core.db.MergedUserInfoDynamoDB;
import com.hello.suripu.core.db.OnlineHmmModelsDAO;
import com.hello.suripu.core.db.OnlineHmmModelsDAODynamoDB;
import com.hello.suripu.core.db.PillDataDAODynamoDB;
import com.hello.suripu.core.db.QuestionResponseDAO;
import com.hello.suripu.core.db.RingTimeHistoryDAODynamoDB;
import com.hello.suripu.core.db.SenseStateDynamoDB;
import com.hello.suripu.core.db.SensorsViewsDynamoDB;
import com.hello.suripu.core.db.SleepStatsDAODynamoDB;
import com.hello.suripu.core.db.TimeZoneHistoryDAODynamoDB;
import com.hello.suripu.core.db.TimelineLogDAO;
import com.hello.suripu.core.db.TrendsInsightsDAO;
import com.hello.suripu.core.db.UserTimelineTestGroupDAO;
import com.hello.suripu.core.db.UserTimelineTestGroupDAOImpl;
import com.hello.suripu.core.db.WifiInfoDAO;
import com.hello.suripu.core.db.WifiInfoDynamoDB;
import com.hello.suripu.core.db.colors.SenseColorDAO;
import com.hello.suripu.core.db.colors.SenseColorDAOSQLImpl;
import com.hello.suripu.core.db.sleep_sounds.DurationDAO;
import com.hello.suripu.core.db.util.JodaArgumentFactory;
import com.hello.suripu.core.db.util.PostgresIntegerArrayArgumentFactory;
import com.hello.suripu.core.flipper.DynamoDBAdapter;
import com.hello.suripu.core.logging.DataLogger;
import com.hello.suripu.core.logging.KinesisLoggerFactory;
import com.hello.suripu.core.models.device.v2.DeviceProcessor;
import com.hello.suripu.core.notifications.MobilePushNotificationProcessor;
import com.hello.suripu.core.notifications.NotificationSubscriptionDAOWrapper;
import com.hello.suripu.core.notifications.NotificationSubscriptionsDAO;

import com.hello.suripu.core.oauth.stores.PersistentApplicationStore;
import com.hello.suripu.core.passwordreset.PasswordResetDB;
import com.hello.suripu.core.pill.heartbeat.PillHeartBeatDAODynamoDB;
import com.hello.suripu.core.preferences.AccountPreferencesDAO;
import com.hello.suripu.core.preferences.AccountPreferencesDynamoDB;
import com.hello.suripu.core.processors.QuestionProcessor;
import com.hello.suripu.core.processors.SleepSoundsProcessor;
import com.hello.suripu.core.processors.TimelineProcessor;
import com.hello.suripu.core.provision.PillProvisionDAO;
import com.hello.suripu.core.store.StoreFeedbackDAO;
import com.hello.suripu.core.support.SupportDAO;
import com.hello.suripu.core.trends.v2.TrendsProcessor;
import com.hello.suripu.core.util.KeyStoreUtils;
import com.hello.suripu.coredw8.clients.AmazonDynamoDBClientFactory;
import com.hello.suripu.coredw8.configuration.S3BucketConfiguration;
import com.hello.suripu.coredw8.configuration.TaimurainHttpClientConfiguration;
import com.hello.suripu.coredw8.db.AccessTokenDAO;
import com.hello.suripu.coredw8.db.SleepHmmDAODynamoDB;
import com.hello.suripu.coredw8.db.TimelineDAODynamoDB;
import com.hello.suripu.coredw8.db.TimelineLogDAODynamoDB;
import com.hello.suripu.coredw8.oauth.AccessToken;
import com.hello.suripu.coredw8.oauth.AuthDynamicFeature;
import com.hello.suripu.coredw8.oauth.AuthValueFactoryProvider;
import com.hello.suripu.coredw8.oauth.OAuthAuthenticator;
import com.hello.suripu.coredw8.oauth.OAuthAuthorizer;
import com.hello.suripu.coredw8.oauth.OAuthCredentialAuthFilter;
import com.hello.suripu.coredw8.oauth.ScopesAllowedDynamicFeature;
import com.hello.suripu.coredw8.oauth.stores.PersistentAccessTokenStore;
import com.hello.suripu.coredw8.util.CustomJSONExceptionMapper;
import com.librato.rollout.RolloutClient;

import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.skife.jdbi.v2.DBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import io.dropwizard.Application;
import io.dropwizard.client.HttpClientBuilder;
import io.dropwizard.jdbi.DBIFactory;
import io.dropwizard.jdbi.ImmutableListContainerFactory;
import io.dropwizard.jdbi.ImmutableSetContainerFactory;
import io.dropwizard.jdbi.OptionalContainerFactory;
import io.dropwizard.jdbi.bundles.DBIExceptionsBundle;
import io.dropwizard.server.AbstractServerFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

public class SuripuApp extends Application<SuripuAppConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SuripuApp.class);

    public static void main(final String[] args) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        new SuripuApp().run(args);
    }

    @Override
    public void initialize(final Bootstrap<SuripuAppConfiguration> bootstrap) {
        bootstrap.addBundle(new DBIExceptionsBundle());
        bootstrap.addCommand(new CreateDynamoDBTables());
        bootstrap.addCommand(new RecreatePillColorCommand());
        bootstrap.addCommand(new MigratePillHeartbeatCommand());
        bootstrap.addCommand(new MigrateDeviceDataCommand());
        bootstrap.addCommand(new MovePillDataToDynamoDBCommand());
    }

    @Override
    public void run(final SuripuAppConfiguration configuration, final Environment environment) throws Exception {

        final DBIFactory factory = new DBIFactory();
        final DBI commonDB = factory.build(environment, configuration.getCommonDB(), "commonDB");
        final DBI insightsDB = factory.build(environment, configuration.getInsightsDB(), "insightsDB");

        commonDB.registerArgumentFactory(new JodaArgumentFactory());
        commonDB.registerContainerFactory(new OptionalContainerFactory());
        commonDB.registerArgumentFactory(new PostgresIntegerArrayArgumentFactory());
        commonDB.registerContainerFactory(new ImmutableListContainerFactory());
        commonDB.registerContainerFactory(new ImmutableSetContainerFactory());

        insightsDB.registerArgumentFactory(new JodaArgumentFactory());
        insightsDB.registerContainerFactory(new OptionalContainerFactory());
        insightsDB.registerArgumentFactory(new PostgresIntegerArrayArgumentFactory());

        final AccountDAO accountDAO = commonDB.onDemand(AccountDAOImpl.class);
        final AccountLocationDAO accountLocationDAO = commonDB.onDemand(AccountLocationDAO.class);
        final ApplicationsDAO applicationsDAO = commonDB.onDemand(ApplicationsDAO.class);
        final AccessTokenDAO accessTokenDAO = commonDB.onDemand(AccessTokenDAO.class);
        final DeviceDAO deviceDAO = commonDB.onDemand(DeviceDAO.class);
        final PillProvisionDAO pillProvisionDAO = commonDB.onDemand(PillProvisionDAO.class);
        final UserTimelineTestGroupDAO userTimelineTestGroupDAO = commonDB.onDemand(UserTimelineTestGroupDAOImpl.class);

        final TrendsInsightsDAO trendsInsightsDAO = insightsDB.onDemand(TrendsInsightsDAO.class);
        final SupportDAO supportDAO = commonDB.onDemand(SupportDAO.class);

        final QuestionResponseDAO questionResponseDAO = insightsDB.onDemand(QuestionResponseDAO.class);
        final FeedbackDAO feedbackDAO = commonDB.onDemand(FeedbackDAO.class);
        final NotificationSubscriptionsDAO notificationSubscriptionsDAO = commonDB.onDemand(NotificationSubscriptionsDAO.class);

        final FileInfoDAO fileInfoDAO = commonDB.onDemand(FileInfoDAO.class);

        final PersistentApplicationStore applicationStore = new PersistentApplicationStore(applicationsDAO);
        final PersistentAccessTokenStore accessTokenStore = new PersistentAccessTokenStore(accessTokenDAO, applicationStore);

        final ClientConfiguration clientConfiguration = new ClientConfiguration();
        clientConfiguration.withConnectionTimeout(200); // in ms
        clientConfiguration.withMaxErrorRetry(1);

        final AWSCredentialsProvider awsCredentialsProvider= new DefaultAWSCredentialsProviderChain();
        final AmazonDynamoDBClientFactory dynamoDBClientFactory = AmazonDynamoDBClientFactory.create(awsCredentialsProvider, new ClientConfiguration(), configuration.dynamoDBConfiguration());
        final AmazonSNSClient snsClient = new AmazonSNSClient(awsCredentialsProvider, clientConfiguration);
        final AmazonKinesisAsyncClient kinesisClient = new AmazonKinesisAsyncClient(awsCredentialsProvider, clientConfiguration);

        final AmazonS3 amazonS3 = new AmazonS3Client(awsCredentialsProvider, clientConfiguration);

        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();

        final AmazonDynamoDB timelineDynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.TIMELINE);
        final TimelineDAODynamoDB timelineDAODynamoDB = new TimelineDAODynamoDB(timelineDynamoDBClient,
            tableNames.get(DynamoDBTableName.TIMELINE),
            configuration.getMaxCacheRefreshDay(),
            environment.metrics());

        final AmazonDynamoDB sleepHmmDynamoDbClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.SLEEP_HMM);
        final SleepHmmDAODynamoDB sleepHmmDAODynamoDB = new SleepHmmDAODynamoDB(sleepHmmDynamoDbClient, tableNames.get(DynamoDBTableName.SLEEP_HMM));

        final AmazonDynamoDB alarmDynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.ALARM);
        final AlarmDAODynamoDB alarmDAODynamoDB = new AlarmDAODynamoDB(alarmDynamoDBClient, tableNames.get(DynamoDBTableName.ALARM));

        final AmazonDynamoDB timezoneHistoryDynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.TIMEZONE_HISTORY);
        final TimeZoneHistoryDAODynamoDB timeZoneHistoryDAODynamoDB = new TimeZoneHistoryDAODynamoDB(timezoneHistoryDynamoDBClient, tableNames.get(DynamoDBTableName.TIMEZONE_HISTORY));

        final AmazonDynamoDB mergedUserInfoDynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.ALARM_INFO);
        final MergedUserInfoDynamoDB mergedUserInfoDynamoDB = new MergedUserInfoDynamoDB(mergedUserInfoDynamoDBClient, tableNames.get(DynamoDBTableName.ALARM_INFO));

        final AmazonDynamoDB insightsDynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.INSIGHTS);
        final InsightsDAODynamoDB insightsDAODynamoDB = new InsightsDAODynamoDB(insightsDynamoDBClient, tableNames.get(DynamoDBTableName.INSIGHTS));

        final AmazonDynamoDB dynamoDBStatsClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.SLEEP_STATS);
        final SleepStatsDAODynamoDB sleepStatsDAODynamoDB = new SleepStatsDAODynamoDB(dynamoDBStatsClient, tableNames.get(DynamoDBTableName.SLEEP_STATS), configuration.getSleepStatsVersion());

        final AmazonDynamoDB ringTimeHistoryDynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.RING_TIME_HISTORY);
        final RingTimeHistoryDAODynamoDB ringTimeHistoryDAODynamoDB = new RingTimeHistoryDAODynamoDB(ringTimeHistoryDynamoDBClient, tableNames.get(DynamoDBTableName.RING_TIME_HISTORY));

        final AmazonDynamoDB appStatsDynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.APP_STATS);
        final AppStatsDAO appStatsDAO = new AppStatsDAODynamoDB(appStatsDynamoDBClient, tableNames.get(DynamoDBTableName.APP_STATS));

        final AmazonDynamoDB deviceDataDAODynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.DEVICE_DATA);
        final DeviceDataDAODynamoDB deviceDataDAODynamoDB = new DeviceDataDAODynamoDB(deviceDataDAODynamoDBClient, tableNames.get(DynamoDBTableName.DEVICE_DATA));

        final AmazonDynamoDB pillDataDAODynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.PILL_DATA);
        final PillDataDAODynamoDB pillDataDAODynamoDB = new PillDataDAODynamoDB(pillDataDAODynamoDBClient, tableNames.get(DynamoDBTableName.PILL_DATA));

        /*  Timeline Log dynamo dB stuff */
        final AmazonDynamoDB timelineLogDynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.TIMELINE_LOG);
        final TimelineLogDAO timelineLogDAO = new TimelineLogDAODynamoDB(timelineLogDynamoDBClient, tableNames.get(DynamoDBTableName.TIMELINE_LOG));

        /* Individual models for users  */
        final AmazonDynamoDB onlineHmmModelsDb = dynamoDBClientFactory.getForTable(DynamoDBTableName.ONLINE_HMM_MODELS);
        final OnlineHmmModelsDAO onlineHmmModelsDAO = OnlineHmmModelsDAODynamoDB.create(onlineHmmModelsDb, tableNames.get(DynamoDBTableName.ONLINE_HMM_MODELS));

        /* Models for feature extraction layer */
        final AmazonDynamoDB featureExtractionModelsDb = dynamoDBClientFactory.getForTable(DynamoDBTableName.FEATURE_EXTRACTION_MODELS);
        final FeatureExtractionModelsDAO featureExtractionDAO = new FeatureExtractionModelsDAODynamoDB(featureExtractionModelsDb, tableNames.get(DynamoDBTableName.FEATURE_EXTRACTION_MODELS));

        /* Default model ensemble for all users  */
        final S3BucketConfiguration timelineModelEnsemblesConfig = configuration.getTimelineModelEnsemblesConfiguration();
        final S3BucketConfiguration seedModelConfig = configuration.getTimelineSeedModelConfiguration();

        final DefaultModelEnsembleDAO defaultModelEnsembleDAO = DefaultModelEnsembleFromS3.create(
            amazonS3,
            timelineModelEnsemblesConfig.getBucket(),
            timelineModelEnsemblesConfig.getKey(),
            seedModelConfig.getBucket(),
            seedModelConfig.getKey()
        );

        final ImmutableMap<QueueName, String> streams = ImmutableMap.copyOf(configuration.getKinesisConfiguration().getStreams());
        final KinesisLoggerFactory kinesisLoggerFactory = new KinesisLoggerFactory(kinesisClient, streams);
        final DataLogger activityLogger = kinesisLoggerFactory.get(QueueName.ACTIVITY_STREAM);
        final DataLogger timelineLogger = kinesisLoggerFactory.get(QueueName.LOGS);

        if(configuration.getMetricsEnabled()) {
            final String graphiteHostName = configuration.getGraphite().getHost();
            final String apiKey = configuration.getGraphite().getApiKey();
            final Integer interval = configuration.getGraphite().getReportingIntervalInSeconds();

            final String env = (configuration.getDebug()) ? "dev" : "prod";
            final String prefix = String.format("%s.%s.suripu-app", apiKey, env);

            final Graphite graphite = new Graphite(new InetSocketAddress(graphiteHostName, 2003));

            final GraphiteReporter reporter = GraphiteReporter.forRegistry(environment.metrics())
                .prefixedWith(prefix)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .filter(MetricFilter.ALL)
                .build(graphite);
            reporter.start(interval, TimeUnit.SECONDS);

            LOGGER.info("Metrics enabled.");
        } else {
            LOGGER.warn("Metrics not enabled.");
        }

        LOGGER.warn("DEBUG MODE = {}", configuration.getDebug());

        //Doing this programmatically instead of in config files
        AbstractServerFactory sf = (AbstractServerFactory) configuration.getServerFactory();
        // disable all default exception mappers
        sf.setRegisterDefaultExceptionMappers(false);

        environment.jersey().register(new CustomJSONExceptionMapper(configuration.getDebug()));

        final PersistentAccessTokenStore tokenStore = new PersistentAccessTokenStore(accessTokenDAO, applicationStore);
        environment.jersey().register(new AuthDynamicFeature(new OAuthCredentialAuthFilter.Builder<AccessToken>()
            .setAuthenticator(new OAuthAuthenticator(tokenStore))
            .setAuthorizer(new OAuthAuthorizer())
            .setRealm("SUPER SECRET STUFF")
            .setPrefix("Bearer")
            .setLogger(activityLogger)
            .buildAuthFilter()));
        environment.jersey().register(ScopesAllowedDynamicFeature.class);
        environment.jersey().register(new AuthValueFactoryProvider.Binder<>(AccessToken.class));

        //TODO: Determine if this is needed
//        environment.getJerseyResourceConfig()
//                .getResourceFilterFactories().add(CacheFilterFactory.class);

        final String namespace = (configuration.getDebug()) ? "dev" : "prod";
        final AmazonDynamoDB featuresDynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.FEATURES);
        final FeatureStore featureStore = new FeatureStore(featuresDynamoDBClient, "features", namespace);

        final RolloutAppModule module = new RolloutAppModule(featureStore, 30);
        ObjectGraphRoot.getInstance().init(module);

        ObjectMapper objectMapper = environment.getObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        environment.jersey().register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(new RolloutClient(new DynamoDBAdapter(featureStore, 30))).to(RolloutClient.class);
            }
        });

        final AmazonDynamoDB senseKeyStoreDynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.SENSE_KEY_STORE);
        final KeyStore senseKeyStore = new KeyStoreDynamoDB(
                senseKeyStoreDynamoDBClient,
            tableNames.get(DynamoDBTableName.SENSE_KEY_STORE),
                "1234567891234567".getBytes(), // TODO: REMOVE THIS WHEN WE ARE NOT SUPPOSED TO HAVE A DEFAULT KEY
                120 // 2 minutes for cache
        );

        final AmazonDynamoDB pillKeyStoreDynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.PILL_KEY_STORE);
        final KeyStore pillKeyStore = new KeyStoreDynamoDB(
                pillKeyStoreDynamoDBClient,
                tableNames.get(DynamoDBTableName.PILL_KEY_STORE),
                "9876543219876543".getBytes(), // TODO: REMOVE THIS WHEN WE ARE NOT SUPPOSED TO HAVE A DEFAULT KEY
                120 // 2 minutes for cache
        );


        final SenseColorDAO senseColorDAO = commonDB.onDemand(SenseColorDAOSQLImpl.class);

        // WARNING: Do not use async methods for anything but SensorsViewsDynamoDB for now
        final AmazonDynamoDBAsync senseLastSeenDynamoDBClient = new AmazonDynamoDBAsyncClient(awsCredentialsProvider, AmazonDynamoDBClientFactory.getDefaultClientConfiguration());
        senseLastSeenDynamoDBClient.setEndpoint(configuration.dynamoDBConfiguration().endpoints().get(DynamoDBTableName.SENSE_LAST_SEEN));

        final SensorsViewsDynamoDB sensorsViewsDynamoDB = new SensorsViewsDynamoDB(
                senseLastSeenDynamoDBClient,
                "", // We are not using dynamodb for minute by minute data yet
                tableNames.get(DynamoDBTableName.SENSE_LAST_SEEN)
        );


        final AmazonDynamoDB calibrationDynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.CALIBRATION);
        final CalibrationDAO calibrationDAO = CalibrationDynamoDB.create(calibrationDynamoDBClient, tableNames.get(DynamoDBTableName.CALIBRATION));

        final AmazonDynamoDB wifiInfoDynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.WIFI_INFO);
        final WifiInfoDAO wifiInfoDAO = new WifiInfoDynamoDB(wifiInfoDynamoDBClient, tableNames.get(DynamoDBTableName.WIFI_INFO));

        // TODO: replace with interface not DAO once namespace don't conflict
        final AmazonDynamoDB pillHeartBeatDynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.PILL_HEARTBEAT);
        final PillHeartBeatDAODynamoDB pillHeartBeatDAODynamoDB = PillHeartBeatDAODynamoDB.create(pillHeartBeatDynamoDBClient, tableNames.get(DynamoDBTableName.PILL_HEARTBEAT));

        final AmazonDynamoDB senseStateDynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.SENSE_STATE);
        final SenseStateDynamoDB senseStateDynamoDB = new SenseStateDynamoDB(senseStateDynamoDBClient, tableNames.get(DynamoDBTableName.SENSE_STATE));

        final AmazonDynamoDB fileManifestDynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.FILE_MANIFEST);
        final FileManifestDAO fileManifestDAO = new FileManifestDynamoDB(fileManifestDynamoDBClient, tableNames.get(DynamoDBTableName.FILE_MANIFEST));

        final AmazonDynamoDB passwordResetDynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.PASSWORD_RESET);
        final PasswordResetDB passwordResetDB = PasswordResetDB.create(passwordResetDynamoDBClient, tableNames.get(DynamoDBTableName.PASSWORD_RESET));

        final AmazonDynamoDB prefsClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.PREFERENCES);
        final AccountPreferencesDAO accountPreferencesDAO = AccountPreferencesDynamoDB.create(prefsClient, tableNames.get(DynamoDBTableName.PREFERENCES));

        if(configuration.getDebug()) {
            environment.jersey().register(new VersionResource());
            environment.jersey().register(new PingResource());
        }

        final MobilePushNotificationProcessor mobilePushNotificationProcessor = new MobilePushNotificationProcessor(snsClient, notificationSubscriptionsDAO);
        final ImmutableMap<String, String> arns = ImmutableMap.copyOf(configuration.getPushNotificationsConfiguration().getArns());
        final NotificationSubscriptionDAOWrapper notificationSubscriptionDAOWrapper = NotificationSubscriptionDAOWrapper.create(
            notificationSubscriptionsDAO,
            snsClient,
            arns
        );
        environment.jersey().register(new MobilePushRegistrationResource(notificationSubscriptionDAOWrapper, mobilePushNotificationProcessor, accountDAO));

        environment.jersey().register(new OAuthResource(accessTokenStore, applicationStore, accountDAO, notificationSubscriptionDAOWrapper));
        environment.jersey().register(new AccountResource(accountDAO, accountLocationDAO));
        environment.jersey().register(new RoomConditionsResource(deviceDataDAODynamoDB, deviceDAO, configuration.getAllowedQueryRange(),senseColorDAO, calibrationDAO));
        environment.jersey().register(new DeviceResources(deviceDAO, mergedUserInfoDynamoDB, sensorsViewsDynamoDB, pillHeartBeatDAODynamoDB));

        final S3BucketConfiguration provisionKeyConfiguration = configuration.getProvisionKeyConfiguration();

        final KeyStoreUtils keyStoreUtils = KeyStoreUtils.build(amazonS3, provisionKeyConfiguration.getBucket(), provisionKeyConfiguration.getKey());
        environment.jersey().register(new ProvisionResource(senseKeyStore, pillKeyStore, keyStoreUtils, pillProvisionDAO, amazonS3));

        /* Neural net endpoint information */
        final TaimurainHttpClientConfiguration taimurainHttpClientConfiguration = configuration.getTaimurainHttpClientConfiguration();
        final TaimurainHttpClient taimurainHttpClient = TaimurainHttpClient.create(
                new HttpClientBuilder(environment).using(taimurainHttpClientConfiguration.getHttpClientConfiguration()).build("taimurain"),
                taimurainHttpClientConfiguration.getEndpoint());

        final TimelineProcessor timelineProcessor = TimelineProcessor.createTimelineProcessor(
                pillDataDAODynamoDB,
                deviceDAO,
                deviceDataDAODynamoDB,
                ringTimeHistoryDAODynamoDB,
                feedbackDAO,
                sleepHmmDAODynamoDB,
                accountDAO,
                sleepStatsDAODynamoDB,
                senseColorDAO,
                onlineHmmModelsDAO,
                featureExtractionDAO,
                calibrationDAO,
                defaultModelEnsembleDAO,
                userTimelineTestGroupDAO,
                taimurainHttpClient);


        environment.jersey().register(new TimelineResource(accountDAO, timelineDAODynamoDB, timelineLogDAO,timelineLogger, timelineProcessor));
        environment.jersey().register(new TimeZoneResource(timeZoneHistoryDAODynamoDB, mergedUserInfoDynamoDB, deviceDAO));
        environment.jersey().register(new AlarmResource(alarmDAODynamoDB, mergedUserInfoDynamoDB, deviceDAO, amazonS3));

        final QuestionProcessor questionProcessor = new QuestionProcessor.Builder()
                .withQuestionResponseDAO(questionResponseDAO)
                .withCheckSkipsNum(configuration.getQuestionConfigs().getNumSkips())
                .withQuestions(questionResponseDAO)
                .build();
        environment.jersey().register(new QuestionsResource(accountDAO, timeZoneHistoryDAODynamoDB, questionProcessor));
        environment.jersey().register(new FeedbackResource(feedbackDAO, timelineDAODynamoDB));
        environment.jersey().register(new AppCheckinResource(2015000000));

        // data science resource stuff
        environment.jersey().register(new AccountPreferencesResource(accountPreferencesDAO));
        environment.jersey().register(new InsightsResource(accountDAO, trendsInsightsDAO, insightsDAODynamoDB, sleepStatsDAODynamoDB));
        environment.jersey().register(new com.hello.suripu.app.v2.InsightsResource(insightsDAODynamoDB, trendsInsightsDAO));
        environment.jersey().register(PasswordResetResource.create(accountDAO, passwordResetDB, configuration.emailConfiguration()));
        environment.jersey().register(new SupportResource(supportDAO));
        environment.jersey().register(new com.hello.suripu.app.v2.TimelineResource(timelineDAODynamoDB, timelineProcessor, timelineLogDAO, feedbackDAO, pillDataDAODynamoDB, sleepStatsDAODynamoDB,timelineLogger));
        final DeviceProcessor deviceProcessor = new DeviceProcessor.Builder()
            .withDeviceDAO(deviceDAO)
            .withMergedUserInfoDynamoDB(mergedUserInfoDynamoDB)
            .withSensorsViewDynamoDB(sensorsViewsDynamoDB)
            .withPillDataDAODynamoDB(pillDataDAODynamoDB)
            .withWifiInfoDAO(wifiInfoDAO)
            .withSenseColorDAO(senseColorDAO)
            .withPillHeartbeatDAO(pillHeartBeatDAODynamoDB)
            .build();
        environment.jersey().register(new DeviceResource(deviceProcessor));
        environment.jersey().register(new com.hello.suripu.app.v2.AccountPreferencesResource(accountPreferencesDAO));
        final StoreFeedbackDAO storeFeedbackDAO = commonDB.onDemand(StoreFeedbackDAO.class);
        environment.jersey().register(new StoreFeedbackResource(storeFeedbackDAO));
        environment.jersey().register(new AppStatsResource(appStatsDAO, insightsDAODynamoDB, questionProcessor, accountDAO, timeZoneHistoryDAODynamoDB));

        final TrendsProcessor trendsProcessor = new TrendsProcessor(sleepStatsDAODynamoDB, accountDAO, timeZoneHistoryDAODynamoDB);
        environment.jersey().register(new TrendsResource(trendsProcessor));

        final DurationDAO durationDAO = commonDB.onDemand(DurationDAO.class);
        final MessejiHttpClientConfiguration messejiHttpClientConfiguration = configuration.getMessejiHttpClientConfiguration();
        final MessejiClient messejiClient = MessejiHttpClient.create(
                new HttpClientBuilder(environment).using(messejiHttpClientConfiguration.getHttpClientConfiguration()).build("messeji"),
                messejiHttpClientConfiguration.getEndpoint());
        environment.jersey().register(SleepSoundsResource.create(
                durationDAO, senseStateDynamoDB, deviceDAO, messejiClient, SleepSoundsProcessor.create(fileInfoDAO, fileManifestDAO),
                configuration.getSleepSoundCacheSeconds(), configuration.getSleepSoundDurationCacheSeconds()));
    }
}
