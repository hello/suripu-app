package com.hello.suripu.app.cli;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeAction;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.AttributeValueUpdate;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.QueryResult;
import com.amazonaws.services.dynamodbv2.model.ReturnValue;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import com.amazonaws.services.dynamodbv2.model.UpdateItemResult;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.hello.suripu.app.configuration.SuripuAppConfiguration;
import com.hello.suripu.core.configuration.DynamoDBTableName;
import com.opencsv.CSVReader;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PopulateInsightsUUIDCommand extends ConfiguredCommand<SuripuAppConfiguration> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PopulateInsightsUUIDCommand.class);

    private static final int MAX_ITERATIONS = 10;
    private static final int NUM_THREADS = 10;

    private final ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);

    private class Insight {
        private Long accountId;
        private String dateCategory;

        private Insight(final Long accountId, final String dateCategory) {
            this.accountId = accountId;
            this.dateCategory = dateCategory;
        }
    }

    private class UpdateResult {
        private Insight insight;
        private Boolean result;

        private UpdateResult(final Insight insight, final Boolean result) {
            this.insight = insight;
            this.result = result;
        }
    }

    public PopulateInsightsUUIDCommand() {
        super("populate_insights_uuid", "add UUID to prod_insights");
    }

    @Override
    public void configure(Subparser subparser) {
        super.configure(subparser);

        subparser.addArgument("--csv")
                .nargs("?")
                .required(true)
                .help("file of all account_ids");

        subparser.addArgument("--account")
                .nargs("?")
                .required(false)
                .help("start from account-id");

    }

    @Override
    protected void run(Bootstrap<SuripuAppConfiguration> bootstrap,
                       Namespace namespace,
                       SuripuAppConfiguration suripuAppConfiguration) throws Exception, IOException {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        final AWSCredentialsProvider awsCredentialsProvider= new DefaultAWSCredentialsProviderChain();
        final String endpoint = suripuAppConfiguration.dynamoDBConfiguration().endpoints().get(DynamoDBTableName.INSIGHTS);
        final String insightsTableName = suripuAppConfiguration.dynamoDBConfiguration().tables().get(DynamoDBTableName.INSIGHTS);

        final AmazonDynamoDB insightsClient = new AmazonDynamoDBClient(awsCredentialsProvider);
        insightsClient.setEndpoint(endpoint);


        final List<Long> accountIds = Lists.newArrayList();
        final File accountsFile = new File(namespace.getString("csv"));
        try (final InputStream input = new FileInputStream(accountsFile);
             final CSVReader reader = new CSVReader(new InputStreamReader(input), ',')) {
            for (final String[] entry : reader) {
                final Long accountId = Long.valueOf(entry[0]);
                accountIds.add(accountId);
            }
        }
        LOGGER.debug("debug=account-ids size={}", accountIds.size());

        final String startAccount = namespace.getString("account");
        final Optional<Long> optionalStartAccount = (startAccount == null) ? Optional.<Long>absent() : Optional.of(Long.valueOf(startAccount));

        populateUUID(accountIds, optionalStartAccount, insightsTableName, insightsClient);

        this.executor.shutdown();
    }


    private void populateUUID(final List<Long> accountIds,
                              final Optional<Long> optionalAccount,
                              final String insightsTableName,
                              final AmazonDynamoDB insightsClient)
            throws IOException, InterruptedException, ExecutionException {

        // get all account-id, date_category for all insights with no UUID
        final Set<String> attributesToGet = Sets.newHashSet("account_id", "date_category", "id");

        int numInsights = 0;
        int numUpdateSuccess = 0;
        int numUpdateFailures = 0;

        for (final Long accountId : accountIds) {

            if (optionalAccount.isPresent() && accountId < optionalAccount.get()) {
                continue;
            }

            // get all insights for this account
            final Map<String, Condition> conditions = Maps.newHashMap();
            conditions.put("account_id", new Condition()
                    .withComparisonOperator(ComparisonOperator.EQ)
                    .withAttributeValueList(new AttributeValue().withN(String.valueOf(accountId))));

            final List<Insight> toUpdate = Lists.newArrayList();

            Map<String, AttributeValue> lastEvaluatedKey = null;
            int numIterations = 0;
            do {
                final QueryRequest queryRequest = new QueryRequest()
                        .withTableName(insightsTableName)
                        .withKeyConditions(conditions)
                        .withAttributesToGet(attributesToGet)
                        .withExclusiveStartKey(lastEvaluatedKey)
                        .withScanIndexForward(true);
                final QueryResult queryResult = insightsClient.query(queryRequest);
                lastEvaluatedKey = queryResult.getLastEvaluatedKey();
                numIterations++;

                if (queryResult.getItems() != null) {
                    final List<Map<String, AttributeValue>> items = queryResult.getItems();
                    for (final Map<String, AttributeValue> item : items) {
                        if (!item.containsKey("id") || item.get("id").getS().isEmpty()) {
                            toUpdate.add(new Insight(Long.valueOf(item.get("account_id").getN()),
                                    item.get("date_category").getS()));
                        }
                    }
                }

            } while (lastEvaluatedKey != null || numIterations > MAX_ITERATIONS);


            final int numItems = toUpdate.size();
            LOGGER.debug("action=updating-insights account_id={} size={}", accountId, numItems);

            final List<Future<UpdateResult>> futures = Lists.newArrayListWithCapacity(toUpdate.size());

            for (final Insight insight : toUpdate) {
                final Future<UpdateResult> future = executor.submit(new Callable<UpdateResult>() {
                    @Override
                    public UpdateResult call() throws Exception {
                        return updateItem(insight, insightsTableName, insightsClient);
                    }
                });
                futures.add(future);
            }

            int updateSuccess = 0;
            int updateFailures = 0;
            for (final Future<UpdateResult> future : futures) {
                final UpdateResult result = future.get();
                if (result != null) {
                    if (result.result) {
                        updateSuccess++;
                    } else {
                        LOGGER.error("error=fail-add-UUID account_id={} date_category={}", result.insight.accountId, result.insight.dateCategory);
                        updateFailures++;
                    }
                }
            }
            numUpdateFailures += updateFailures;
            numUpdateSuccess += updateSuccess;
            numInsights += toUpdate.size();

            LOGGER.debug("action=updated-UUID account_id={} success={} failure={}", accountId, updateSuccess, updateFailures);
        }
        LOGGER.debug("info=insights-processed size={} success={} failures={}", numInsights, numUpdateSuccess, numUpdateFailures);
    }

    private UpdateResult updateItem(final Insight insight, final String tableName, final AmazonDynamoDB insightsClient) {
        final Map<String, AttributeValue> key = Maps.newHashMap();
        key.put("account_id", new AttributeValue().withN(String.valueOf(insight.accountId)));
        key.put("date_category", new AttributeValue().withS(insight.dateCategory));

        final Map<String, AttributeValueUpdate> updateValue = Maps.newHashMap();
        final String id = UUID.randomUUID().toString();
        updateValue.put("id", new AttributeValueUpdate()
                .withAction(AttributeAction.PUT)
                .withValue(new AttributeValue().withS(id)));

        final UpdateItemRequest updateItemRequest = new UpdateItemRequest()
                .withTableName(tableName)
                .withKey(key)
                .withAttributeUpdates(updateValue)
                .withReturnValues(ReturnValue.ALL_NEW);

        final UpdateItemResult result = insightsClient.updateItem(updateItemRequest);
        final Map<String, AttributeValue> item = result.getAttributes();
        if (item != null) {
            if (item.containsKey("id") && item.get("id").getS().equals(id)) {
                return new UpdateResult(insight, true);
            }
        }
        return new UpdateResult(insight, false);
    }
}
