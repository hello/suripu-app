package com.hello.suripu.app.cli;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.CreateTableResult;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.google.common.collect.ImmutableMap;
import com.hello.suripu.app.configuration.SuripuAppConfiguration;
import com.hello.suripu.core.analytics.AnalyticsTrackingDynamoDB;
import com.hello.suripu.core.configuration.DynamoDBTableName;
import com.hello.suripu.core.db.AggStatsDAODynamoDB;
import com.hello.suripu.core.db.AggregateSleepScoreDAODynamoDB;
import com.hello.suripu.core.db.AlarmDAODynamoDB;
import com.hello.suripu.core.db.AlgorithmResultsDAODynamoDB;
import com.hello.suripu.core.db.AppStatsDAODynamoDB;
import com.hello.suripu.core.db.CalibrationDynamoDB;
import com.hello.suripu.core.db.DeviceDataDAODynamoDB;
import com.hello.suripu.core.db.FeatureExtractionModelsDAODynamoDB;
import com.hello.suripu.core.db.FeatureStore;
import com.hello.suripu.core.db.FileManifestDynamoDB;
import com.hello.suripu.core.db.InsightsDAODynamoDB;
import com.hello.suripu.core.db.KeyStoreDynamoDB;
import com.hello.suripu.core.db.MarketingInsightsSeenDAODynamoDB;
import com.hello.suripu.core.db.MergedUserInfoDynamoDB;
import com.hello.suripu.core.db.OTAHistoryDAODynamoDB;
import com.hello.suripu.core.db.OnlineHmmModelsDAODynamoDB;
import com.hello.suripu.core.db.PillDataDAODynamoDB;
import com.hello.suripu.core.db.ResponseCommandsDAODynamoDB;
import com.hello.suripu.core.db.RingTimeHistoryDAODynamoDB;
import com.hello.suripu.core.db.ScheduledRingTimeHistoryDAODynamoDB;
import com.hello.suripu.core.db.SenseStateDynamoDB;
import com.hello.suripu.core.db.SensorsViewsDynamoDB;
import com.hello.suripu.core.db.SleepScoreParametersDynamoDB;
import com.hello.suripu.core.db.SleepStatsDAODynamoDB;
import com.hello.suripu.core.db.SmartAlarmLoggerDynamoDB;
import com.hello.suripu.core.db.TeamStore;
import com.hello.suripu.core.db.TimeZoneHistoryDAODynamoDB;
import com.hello.suripu.core.db.WifiInfoDynamoDB;
import com.hello.suripu.core.insights.InsightsLastSeenDynamoDB;
import com.hello.suripu.core.passwordreset.PasswordResetDB;
import com.hello.suripu.core.pill.heartbeat.PillHeartBeatDAODynamoDB;
import com.hello.suripu.core.preferences.AccountPreferencesDynamoDB;
import com.hello.suripu.core.profile.ProfilePhotoStore;
import com.hello.suripu.core.profile.ProfilePhotoStoreDynamoDB;
import com.hello.suripu.core.speech.SpeechResultDAODynamoDB;
import com.hello.suripu.core.swap.ddb.DynamoDBSwapper;
import com.hello.suripu.coredropwizard.db.SleepHmmDAODynamoDB;
import com.hello.suripu.coredropwizard.db.TimelineDAODynamoDB;
import com.hello.suripu.coredropwizard.db.TimelineLogDAODynamoDB;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class CreateDynamoDBTables extends ConfiguredCommand<SuripuAppConfiguration> {

    public CreateDynamoDBTables() {
        super("create_dynamodb_tables", "Create dynamoDB tables");
    }

    @Override
    protected void run(Bootstrap<SuripuAppConfiguration> bootstrap, Namespace namespace, SuripuAppConfiguration configuration) throws Exception {
        final AWSCredentialsProvider awsCredentialsProvider = new DefaultAWSCredentialsProviderChain();

        createUserInfoTable(configuration, awsCredentialsProvider);
        createAlarmTable(configuration, awsCredentialsProvider);
        createFeaturesTable(configuration, awsCredentialsProvider);
        createTeamsTable(configuration, awsCredentialsProvider);
        createSleepScoreTable(configuration, awsCredentialsProvider);
        createScheduledRingTimeHistoryTable(configuration, awsCredentialsProvider);
        createTimeZoneHistoryTable(configuration, awsCredentialsProvider);
        createInsightsTable(configuration, awsCredentialsProvider);
        createAccountPreferencesTable(configuration, awsCredentialsProvider);
        createSenseKeyStoreTable(configuration, awsCredentialsProvider);
        createPillKeyStoreTable(configuration, awsCredentialsProvider);
        createTimelineTable(configuration, awsCredentialsProvider);
        createPasswordResetTable(configuration, awsCredentialsProvider);
        createSleepHmmTable(configuration, awsCredentialsProvider);
        createRingTimeHistoryTable(configuration, awsCredentialsProvider);
        createSleepStatsTable(configuration, awsCredentialsProvider);
        createAlgorithmTestTable(configuration, awsCredentialsProvider);
        createTimelineLogTable(configuration, awsCredentialsProvider);
        createSmartAlarmLogTable(configuration, awsCredentialsProvider);
        createOTAHistoryTable(configuration, awsCredentialsProvider);
        createResponseCommandsTable(configuration, awsCredentialsProvider);
        // This should not be in app
        // createFWUpgradePathTable(configuration, awsCredentialsProvider);
        createFeatureExtractionModelsTable(configuration, awsCredentialsProvider);
        createOnlineHmmModelsTable(configuration,awsCredentialsProvider);
        createCalibrationTable(configuration, awsCredentialsProvider);
        createWifiInfoTable(configuration, awsCredentialsProvider);
        createAppStatsTable(configuration, awsCredentialsProvider);
        createPillHeartBeatTable(configuration, awsCredentialsProvider);
        createDeviceDataTable(configuration, awsCredentialsProvider);
        createAggStatsTable(configuration, awsCredentialsProvider);
        createLastSeenTable(configuration, awsCredentialsProvider);
        createPillDataTable(configuration, awsCredentialsProvider);
        createSenseStateTable(configuration, awsCredentialsProvider);
        createFileManifestTable(configuration, awsCredentialsProvider);
        createMarketingInsightsSeenTable(configuration, awsCredentialsProvider);
        createSleepScoreParametersTable(configuration, awsCredentialsProvider);
        createProfilePhotoTable(configuration, awsCredentialsProvider);
        createInsightsLastSeenTable(configuration, awsCredentialsProvider);
        createSpeechResultsTable(configuration, awsCredentialsProvider);
        createAnalyticsTrackingTable(configuration, awsCredentialsProvider);
        createSwapIntentsTable(configuration, awsCredentialsProvider);
    }

    private void createSmartAlarmLogTable(final SuripuAppConfiguration configuration, final AWSCredentialsProvider awsCredentialsProvider){
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.SMART_ALARM_LOG);
        final String endpoint = endpoints.get(DynamoDBTableName.SMART_ALARM_LOG);
        client.setEndpoint(endpoint);
        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            final CreateTableResult result = SmartAlarmLoggerDynamoDB.createTable(tableName, client);
            final TableDescription description = result.getTableDescription();
            System.out.println(description.getTableStatus());
        }
    }

    private void createAccountPreferencesTable(final SuripuAppConfiguration configuration, final AWSCredentialsProvider awsCredentialsProvider) {
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.PREFERENCES);
        final String endpoint = endpoints.get(DynamoDBTableName.PREFERENCES);
        client.setEndpoint(endpoint);
        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            final CreateTableResult result = AccountPreferencesDynamoDB.createTable(tableName, client);
            final TableDescription description = result.getTableDescription();
            System.out.println(description.getTableStatus());
        }
    }

    private void createScheduledRingTimeHistoryTable(final SuripuAppConfiguration configuration, final AWSCredentialsProvider awsCredentialsProvider) {
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.RING_TIME_HISTORY);
        final String endpoint = endpoints.get(DynamoDBTableName.RING_TIME_HISTORY);
        client.setEndpoint(endpoint);
        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            final CreateTableResult result = ScheduledRingTimeHistoryDAODynamoDB.createTable(tableName, client);
            final TableDescription description = result.getTableDescription();
            System.out.println(description.getTableStatus());
        }
    }

    private void createSleepScoreTable(final SuripuAppConfiguration configuration, final AWSCredentialsProvider awsCredentialsProvider) {
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String version = configuration.getSleepScoreVersion();
        final String tableName = tableNames.get(DynamoDBTableName.SLEEP_SCORE) + "_" + version;
        final String endpoint = endpoints.get(DynamoDBTableName.SLEEP_SCORE);
        client.setEndpoint(endpoint);

        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            final CreateTableResult result = AggregateSleepScoreDAODynamoDB.createTable(tableName, client);
            final TableDescription description = result.getTableDescription();
            System.out.println(description.getTableStatus());
        }
    }

    private void createAlgorithmTestTable(final SuripuAppConfiguration configuration, final AWSCredentialsProvider awsCredentialsProvider) {
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.ALGORITHM_TEST);
        final String endpoint = endpoints.get(DynamoDBTableName.ALGORITHM_TEST);
        client.setEndpoint(endpoint);
        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            final CreateTableResult result = AlgorithmResultsDAODynamoDB.createTable(tableName, client);
            final TableDescription description = result.getTableDescription();
            System.out.println(description.getTableStatus());
        }
    }

    private void createInsightsTable(final SuripuAppConfiguration configuration, final AWSCredentialsProvider awsCredentialsProvider) {
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.INSIGHTS);
        final String endpoint = endpoints.get(DynamoDBTableName.INSIGHTS);
        client.setEndpoint(endpoint);
        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            final CreateTableResult result = InsightsDAODynamoDB.createTable(tableName, client);
            final TableDescription description = result.getTableDescription();
            System.out.println(description.getTableStatus());
        }
    }

    private void createTeamsTable(final SuripuAppConfiguration configuration, final AWSCredentialsProvider awsCredentialsProvider) {

        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.TEAMS);
        final String endpoint = endpoints.get(DynamoDBTableName.TEAMS);
        client.setEndpoint(endpoint);
        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            final CreateTableResult result = TeamStore.createTable(tableName, client);
            final TableDescription description = result.getTableDescription();
            System.out.println(description.getTableStatus());
        }
    }

    private void createUserInfoTable(final SuripuAppConfiguration configuration, final AWSCredentialsProvider awsCredentialsProvider) {

        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.ALARM_INFO);
        final String endpoint = endpoints.get(DynamoDBTableName.ALARM_INFO);
        client.setEndpoint(endpoint);
        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            final CreateTableResult result = MergedUserInfoDynamoDB.createTable(tableName, client);
            final TableDescription description = result.getTableDescription();
            System.out.println(description.getTableStatus());
        }
    }

    private void createFeaturesTable(final SuripuAppConfiguration configuration, final AWSCredentialsProvider awsCredentialsProvider) {

        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.FEATURES);
        final String endpoint = endpoints.get(DynamoDBTableName.FEATURES);
        client.setEndpoint(endpoint);
        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            final CreateTableResult result = FeatureStore.createTable(tableName, client);
            final TableDescription description = result.getTableDescription();
            System.out.println(description.getTableStatus());
        }
    }

    private void createAlarmTable(final SuripuAppConfiguration configuration, final AWSCredentialsProvider awsCredentialsProvider) {

        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.ALARM);
        final String endpoint = endpoints.get(DynamoDBTableName.ALARM);
        client.setEndpoint(endpoint);
        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            final CreateTableResult result = AlarmDAODynamoDB.createTable(tableName, client);
            final TableDescription description = result.getTableDescription();
            System.out.println(description.getTableStatus());
        }
    }

    private void createTimeZoneHistoryTable(final SuripuAppConfiguration configuration, final AWSCredentialsProvider awsCredentialsProvider) {

        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.TIMEZONE_HISTORY);
        final String endpoint = endpoints.get(DynamoDBTableName.TIMEZONE_HISTORY);
        client.setEndpoint(endpoint);
        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            final CreateTableResult result = TimeZoneHistoryDAODynamoDB.createTable(tableName, client);
            final TableDescription description = result.getTableDescription();
            System.out.println(description.getTableStatus());
        }
    }

    private void createSenseKeyStoreTable(final SuripuAppConfiguration configuration, final AWSCredentialsProvider awsCredentialsProvider) {
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.SENSE_KEY_STORE);
        final String endpoint = endpoints.get(DynamoDBTableName.SENSE_KEY_STORE);
        client.setEndpoint(endpoint);
        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            final CreateTableResult result = KeyStoreDynamoDB.createTable(tableName, client);
            final TableDescription description = result.getTableDescription();
            System.out.println(description.getTableStatus());
        }
    }

    private void createPillKeyStoreTable(final SuripuAppConfiguration configuration, final AWSCredentialsProvider awsCredentialsProvider) {
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.PILL_KEY_STORE);
        final String endpoint = endpoints.get(DynamoDBTableName.PILL_KEY_STORE);
        client.setEndpoint(endpoint);
        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            final CreateTableResult result = KeyStoreDynamoDB.createTable(tableName, client);
            final TableDescription description = result.getTableDescription();
            System.out.println(description.getTableStatus());
        }
    }

    private void createTimelineTable(final SuripuAppConfiguration configuration, final AWSCredentialsProvider awsCredentialsProvider) {
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.TIMELINE);
        final String endpoint = endpoints.get(DynamoDBTableName.TIMELINE);
        client.setEndpoint(endpoint);
        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            final CreateTableResult result = TimelineDAODynamoDB.createTable(tableName, client);
            final TableDescription description = result.getTableDescription();
            System.out.println(description.getTableStatus());
        }
    }


    private void createPasswordResetTable(final SuripuAppConfiguration configuration, final AWSCredentialsProvider awsCredentialsProvider) {
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.PASSWORD_RESET);
        final String endpoint = endpoints.get(DynamoDBTableName.PASSWORD_RESET);
        client.setEndpoint(endpoint);
        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            final CreateTableResult result = PasswordResetDB.createTable(tableName, client);
            final TableDescription description = result.getTableDescription();
            System.out.println("Table: " + tableName + " " + description.getTableStatus());
        }
    }

    private void createRingTimeHistoryTable(final SuripuAppConfiguration configuration, final AWSCredentialsProvider awsCredentialsProvider){
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.RING_TIME_HISTORY);
        final String endpoint = endpoints.get(DynamoDBTableName.RING_TIME_HISTORY);
        client.setEndpoint(endpoint);
        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            final CreateTableResult result = RingTimeHistoryDAODynamoDB.createTable(tableName, client);
            final TableDescription description = result.getTableDescription();
            System.out.println("Table: " + tableName + " " + description.getTableStatus());
        }
    }

    private void createSleepHmmTable(final SuripuAppConfiguration configuration, final AWSCredentialsProvider awsCredentialsProvider) {
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.SLEEP_HMM);
        final String endpoint = endpoints.get(DynamoDBTableName.SLEEP_HMM);
        client.setEndpoint(endpoint);

        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            final CreateTableResult result = SleepHmmDAODynamoDB.createTable(tableName, client);
            final TableDescription description = result.getTableDescription();
            System.out.println(description.getTableStatus());
        }
    }

    private void createSleepStatsTable(final SuripuAppConfiguration configuration, final AWSCredentialsProvider awsCredentialsProvider) {
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String version = configuration.getSleepStatsVersion();
        final String tableName = tableNames.get(DynamoDBTableName.SLEEP_STATS) + "_" + version;
        final String endpoint = endpoints.get(DynamoDBTableName.SLEEP_STATS);
        client.setEndpoint(endpoint);

        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            final CreateTableResult result = SleepStatsDAODynamoDB.createTable(tableName, client);
            final TableDescription description = result.getTableDescription();
            System.out.println(description.getTableStatus());
        }
    }

    private void createTimelineLogTable(final SuripuAppConfiguration configuration, final AWSCredentialsProvider awsCredentialsProvider) {
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.TIMELINE_LOG);
        final String endpoint = endpoints.get(DynamoDBTableName.TIMELINE_LOG);
        client.setEndpoint(endpoint);

        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            final CreateTableResult result = TimelineLogDAODynamoDB.createTable(tableName, client);
            final TableDescription description = result.getTableDescription();
            System.out.println(description.getTableStatus());
        }
    }

    private void createOTAHistoryTable(final SuripuAppConfiguration configuration, final AWSCredentialsProvider awsCredentialsProvider) {
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.OTA_HISTORY);
        final String endpoint = endpoints.get(DynamoDBTableName.OTA_HISTORY);
        client.setEndpoint(endpoint);

        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            final CreateTableResult result = OTAHistoryDAODynamoDB.createTable(tableName, client);
            final TableDescription description = result.getTableDescription();
            System.out.println(description.getTableStatus());
        }
    }

    private void createResponseCommandsTable(final SuripuAppConfiguration configuration, final AWSCredentialsProvider awsCredentialsProvider) {
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.SYNC_RESPONSE_COMMANDS);
        final String endpoint = endpoints.get(DynamoDBTableName.SYNC_RESPONSE_COMMANDS);
        client.setEndpoint(endpoint);

        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            final CreateTableResult result = ResponseCommandsDAODynamoDB.createTable(tableName, client);
            final TableDescription description = result.getTableDescription();
            System.out.println(description.getTableStatus());
        }
    }

//    private void createFWUpgradePathTable(final SuripuAppConfiguration configuration, final AWSCredentialsProvider awsCredentialsProvider) {
//        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
//        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
//        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();
//
//        final String tableName = tableNames.get(DynamoDBTableName.FIRMWARE_UPGRADE_PATH);
//        final String endpoint = endpoints.get(DynamoDBTableName.FIRMWARE_UPGRADE_PATH);
//        client.setEndpoint(endpoint);
//
//        try {
//            client.describeTable(tableName);
//            System.out.println(String.format("%s already exists.", tableName));
//        } catch (AmazonServiceException exception) {
//            final CreateTableResult result = FirmwareUpgradePathDAO.createTable(tableName, client);
//            final TableDescription description = result.getTableDescription();
//            System.out.println(description.getTableStatus());
//        }
//    }


    private void createFeatureExtractionModelsTable(final SuripuAppConfiguration configuration, final AWSCredentialsProvider awsCredentialsProvider) {
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.FEATURE_EXTRACTION_MODELS);
        final String endpoint = endpoints.get(DynamoDBTableName.FEATURE_EXTRACTION_MODELS);
        client.setEndpoint(endpoint);

        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            final CreateTableResult result = FeatureExtractionModelsDAODynamoDB.createTable(tableName, client);
            final TableDescription description = result.getTableDescription();
            System.out.println(description.getTableStatus());
        }
    }

    private void createOnlineHmmModelsTable(final SuripuAppConfiguration configuration, final AWSCredentialsProvider awsCredentialsProvider) {
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.ONLINE_HMM_MODELS);
        final String endpoint = endpoints.get(DynamoDBTableName.ONLINE_HMM_MODELS);
        client.setEndpoint(endpoint);

        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            final CreateTableResult result = OnlineHmmModelsDAODynamoDB.createTable(tableName, client);
            final TableDescription description = result.getTableDescription();
            System.out.println(description.getTableStatus());
        }
    }


    private void createCalibrationTable(final SuripuAppConfiguration configuration, final AWSCredentialsProvider awsCredentialsProvider) {
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.CALIBRATION);
        final String endpoint = endpoints.get(DynamoDBTableName.CALIBRATION);
        client.setEndpoint(endpoint);

        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            final CreateTableResult result = CalibrationDynamoDB.createTable(tableName, client);
            final TableDescription description = result.getTableDescription();
            System.out.println(description.getTableStatus());
        }
    }

    private void createWifiInfoTable(final SuripuAppConfiguration configuration, final AWSCredentialsProvider awsCredentialsProvider) {
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.WIFI_INFO);
        final String endpoint = endpoints.get(DynamoDBTableName.WIFI_INFO);
        client.setEndpoint(endpoint);

        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            final CreateTableResult result = WifiInfoDynamoDB.createTable(tableName, client);
            final TableDescription description = result.getTableDescription();
            System.out.println(description.getTableStatus());
        }
    }

    private void createAppStatsTable(final SuripuAppConfiguration configuration, final AWSCredentialsProvider awsCredentialsProvider) {
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.APP_STATS);
        final String endpoint = endpoints.get(DynamoDBTableName.APP_STATS);
        client.setEndpoint(endpoint);

        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            final CreateTableResult result = AppStatsDAODynamoDB.createTable(tableName, client);
            final TableDescription description = result.getTableDescription();
            System.out.println(description.getTableStatus());
        }
    }

    private void createPillHeartBeatTable(final SuripuAppConfiguration configuration, final AWSCredentialsProvider awsCredentialsProvider) {
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.PILL_HEARTBEAT);
        final String endpoint = endpoints.get(DynamoDBTableName.PILL_HEARTBEAT);
        client.setEndpoint(endpoint);

        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            final CreateTableResult result = PillHeartBeatDAODynamoDB.createTable(tableName, client);
            final TableDescription description = result.getTableDescription();
            System.out.println(description.getTableStatus());
        }
    }

    private void createDeviceDataTable(final SuripuAppConfiguration configuration, final AWSCredentialsProvider awsCredentialsProvider) {
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tablePrefix = tableNames.get(DynamoDBTableName.DEVICE_DATA);
        final String endpoint = endpoints.get(DynamoDBTableName.DEVICE_DATA);
        client.setEndpoint(endpoint);
        final DeviceDataDAODynamoDB deviceDataDAODynamoDB = new DeviceDataDAODynamoDB(client, tablePrefix);

        final DateTime now = DateTime.now(DateTimeZone.UTC);

        // Create 6 months worth of tables
        for (int i = 0; i < 6; i++) {
            final DateTime currDateTime = now.plusMonths(i);
            try {
                client.describeTable(deviceDataDAODynamoDB.getTableName(currDateTime));
                System.out.println(String.format("%s already exists.", deviceDataDAODynamoDB.getTableName(currDateTime)));
            } catch (AmazonServiceException exception) {
                final String tableName = deviceDataDAODynamoDB.getTableName(currDateTime);
                final CreateTableResult result = deviceDataDAODynamoDB.createTable(tableName);
                System.out.println(result.getTableDescription().getTableStatus());
            }
        }
    }

    private void createAggStatsTable(final SuripuAppConfiguration configuration, final AWSCredentialsProvider awsCredentialsProvider) {
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tablePrefix = tableNames.get(DynamoDBTableName.AGG_STATS);
        final String version = configuration.getAggStatsVersion();
        final String endpoint = endpoints.get(DynamoDBTableName.AGG_STATS);
        client.setEndpoint(endpoint);
        final AggStatsDAODynamoDB aggStatsDAODynamoDB = new AggStatsDAODynamoDB(client, tablePrefix, version);

        final DateTime now = DateTime.now(DateTimeZone.UTC);

        try {
            client.describeTable(aggStatsDAODynamoDB.getTableName(now));
            System.out.println(String.format("%s already exists.", aggStatsDAODynamoDB.getTableName(now)));
        } catch (AmazonServiceException exception) {
            final String tableName = aggStatsDAODynamoDB.getTableName(now);
            final CreateTableResult result = aggStatsDAODynamoDB.createTable(tableName);
            System.out.println(result.getTableDescription().getTableStatus());
        }
    }

    private void createLastSeenTable(final SuripuAppConfiguration configuration, final AWSCredentialsProvider awsCredentialsProvider) {
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.SENSE_LAST_SEEN);
        final String endpoint = endpoints.get(DynamoDBTableName.SENSE_LAST_SEEN);
        client.setEndpoint(endpoint);
        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            final CreateTableResult result = SensorsViewsDynamoDB.createLastSeenTable(tableName, client);
            final TableDescription description = result.getTableDescription();
            System.out.println(description.getTableStatus());
        }
    }

    private void createPillDataTable(SuripuAppConfiguration configuration, AWSCredentialsProvider awsCredentialsProvider) {
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.PILL_DATA);
        final String endpoint = endpoints.get(DynamoDBTableName.PILL_DATA);
        client.setEndpoint(endpoint);

        final DateTime now = DateTime.now(DateTimeZone.UTC);
        final PillDataDAODynamoDB pillDataDynamoDB = new PillDataDAODynamoDB(client, tableName);
        for (int i = 0; i < 6; i++) {
            final DateTime currDateTime = now.plusMonths(i);
            final String currentTablename = pillDataDynamoDB.getTableName(currDateTime);
            System.out.println(String.format("Creating table %s", currentTablename));
            try {
                client.describeTable(currentTablename);
                System.out.println(String.format("%s already exists.", currentTablename));
            } catch (AmazonServiceException exception) {
                final CreateTableResult result = pillDataDynamoDB.createTable(currentTablename);
                final TableDescription description = result.getTableDescription();
                System.out.println(description.getTableStatus());
            }
        }
    }

    private void createSenseStateTable(SuripuAppConfiguration configuration, AWSCredentialsProvider awsCredentialsProvider) {
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.SENSE_STATE);
        final String endpoint = endpoints.get(DynamoDBTableName.SENSE_STATE);
        client.setEndpoint(endpoint);

        final SenseStateDynamoDB senseStateDynamoDB = new SenseStateDynamoDB(client, tableName);
        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            final CreateTableResult result = senseStateDynamoDB.createTable(1L, 1L);
            final TableDescription description = result.getTableDescription();
            System.out.println(description.getTableStatus());
        }
    }

    private void createFileManifestTable(SuripuAppConfiguration configuration, AWSCredentialsProvider awsCredentialsProvider) {
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.FILE_MANIFEST);
        final String endpoint = endpoints.get(DynamoDBTableName.FILE_MANIFEST);
        client.setEndpoint(endpoint);

        final FileManifestDynamoDB fileManifestDynamoDB = new FileManifestDynamoDB(client, tableName);
        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            final CreateTableResult result = fileManifestDynamoDB.createTable(1L, 1L);
            final TableDescription description = result.getTableDescription();
            System.out.println(description.getTableStatus());
        }
    }

    private void createMarketingInsightsSeenTable(SuripuAppConfiguration configuration, AWSCredentialsProvider awsCredentialsProvider) {
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.MARKETING_INSIGHTS_SEEN);
        final String endpoint = endpoints.get(DynamoDBTableName.MARKETING_INSIGHTS_SEEN);
        client.setEndpoint(endpoint);

        final MarketingInsightsSeenDAODynamoDB dynamoDB = new MarketingInsightsSeenDAODynamoDB(client, tableName);
        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            final CreateTableResult result = dynamoDB.createTable(1L, 1L);
            final TableDescription description = result.getTableDescription();
            System.out.println(description.getTableStatus());
        }
    }

    private void createSleepScoreParametersTable(SuripuAppConfiguration configuration, AWSCredentialsProvider awsCredentialsProvider) {
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.SLEEP_SCORE_PARAMETERS);
        final String endpoint = endpoints.get(DynamoDBTableName.SLEEP_SCORE_PARAMETERS);
        client.setEndpoint(endpoint);

        final SleepScoreParametersDynamoDB dynamoDB = new SleepScoreParametersDynamoDB(client, tableName);
        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            final CreateTableResult result = dynamoDB.createTable(1L, 1L);
            final TableDescription description = result.getTableDescription();
            System.out.println(description.getTableStatus());
        }
    }

    private void createProfilePhotoTable(SuripuAppConfiguration configuration, AWSCredentialsProvider awsCredentialsProvider) throws InterruptedException {
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.PROFILE_PHOTO);
        final String endpoint = endpoints.get(DynamoDBTableName.PROFILE_PHOTO);
        client.setEndpoint(endpoint);

        final ProfilePhotoStore profilePhotoStoreDynamoDB = ProfilePhotoStoreDynamoDB.create(client, tableName);
        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            ProfilePhotoStoreDynamoDB.createTable(client, tableName);
            System.out.println(String.format("%s created", tableName));
        }
    }

    private void createInsightsLastSeenTable(SuripuAppConfiguration configuration, AWSCredentialsProvider awsCredentialsProvider) throws InterruptedException {
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.INSIGHTS_LAST_SEEN);
        final String endpoint = endpoints.get(DynamoDBTableName.INSIGHTS_LAST_SEEN);
        client.setEndpoint(endpoint);

        final InsightsLastSeenDynamoDB insightsLastSeenDynamoDB = InsightsLastSeenDynamoDB.create(client, tableName);
        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            InsightsLastSeenDynamoDB.createTable(client, tableName);
            System.out.println(String.format("%s created", tableName));
        }
    }

    private void createSpeechResultsTable(SuripuAppConfiguration configuration, AWSCredentialsProvider awsCredentialsProvider) throws InterruptedException {
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.SPEECH_RESULTS);
        final String endpoint = endpoints.get(DynamoDBTableName.SPEECH_RESULTS);
        client.setEndpoint(endpoint);

        final SpeechResultDAODynamoDB speechResultDAODynamoDB = SpeechResultDAODynamoDB.create(client, tableName);
        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            speechResultDAODynamoDB.createTable(client, tableName);
            System.out.println(String.format("%s created", tableName));
        }
    }

    private void createAnalyticsTrackingTable(SuripuAppConfiguration configuration, AWSCredentialsProvider awsCredentialsProvider) throws InterruptedException {
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.ANALYTICS_TRACKING);
        final String endpoint = endpoints.get(DynamoDBTableName.ANALYTICS_TRACKING);
        client.setEndpoint(endpoint);

        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            AnalyticsTrackingDynamoDB.createTable(client, tableName);
            System.out.println(String.format("%s created", tableName));
        }
    }

    private void createSwapIntentsTable(SuripuAppConfiguration configuration, AWSCredentialsProvider awsCredentialsProvider) throws InterruptedException {
        final AmazonDynamoDBClient client = new AmazonDynamoDBClient(awsCredentialsProvider);
        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final ImmutableMap<DynamoDBTableName, String> endpoints = configuration.dynamoDBConfiguration().endpoints();

        final String tableName = tableNames.get(DynamoDBTableName.SWAP_INTENTS);
        final String endpoint = endpoints.get(DynamoDBTableName.SWAP_INTENTS);
        client.setEndpoint(endpoint);

        try {
            client.describeTable(tableName);
            System.out.println(String.format("%s already exists.", tableName));
        } catch (AmazonServiceException exception) {
            DynamoDBSwapper.createTable(tableName, client);
            System.out.println(String.format("%s created", tableName));
        }
    }
}
