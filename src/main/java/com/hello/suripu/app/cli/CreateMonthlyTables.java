package com.hello.suripu.app.cli;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.CreateTableResult;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.google.common.collect.ImmutableMap;
import com.hello.suripu.app.configuration.SuripuAppConfiguration;
import com.hello.suripu.core.configuration.DynamoDBTableName;
import com.hello.suripu.core.db.DeviceDataDAODynamoDB;
import com.hello.suripu.core.db.PillDataDAODynamoDB;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class CreateMonthlyTables extends ConfiguredCommand<SuripuAppConfiguration> {

    public CreateMonthlyTables() {
        super("create_monthly_tables", "Create pill and sense monthly dynamoDB tables");
    }

    @Override
    protected void run(Bootstrap<SuripuAppConfiguration> bootstrap, Namespace namespace, SuripuAppConfiguration configuration) throws Exception {
        final AWSCredentialsProvider awsCredentialsProvider = new DefaultAWSCredentialsProviderChain();
        createPillDataTable(configuration, awsCredentialsProvider);
        createDeviceDataTable(configuration, awsCredentialsProvider);
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
            try {
                client.describeTable(currentTablename);
                System.out.println(String.format("%s already exists.", currentTablename));
            } catch (AmazonServiceException exception) {
                System.out.println(String.format("creating %s", currentTablename));
                final CreateTableResult result = pillDataDynamoDB.createTable(currentTablename);
                final TableDescription description = result.getTableDescription();
                System.out.println(description.getTableStatus());
            }
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
                System.out.println(String.format("creating %s.", deviceDataDAODynamoDB.getTableName(currDateTime)));
                final String tableName = deviceDataDAODynamoDB.getTableName(currDateTime);
                final CreateTableResult result = deviceDataDAODynamoDB.createTable(tableName);
                System.out.println(result.getTableDescription().getTableStatus());
            }
        }
    }
}
