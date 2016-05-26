package com.hello.suripu.app.cli;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.google.common.collect.ImmutableMap;
import com.hello.suripu.app.configuration.SuripuAppConfiguration;
import com.hello.suripu.core.configuration.DynamoDBTableName;
import com.hello.suripu.core.db.AccountDAO;
import com.hello.suripu.core.db.AccountDAOImpl;
import com.hello.suripu.core.db.QuestionResponseDAO;
import com.hello.suripu.core.db.SleepScoreParametersDynamoDB;
import com.hello.suripu.core.db.SleepStatsDAODynamoDB;
import com.hello.suripu.core.db.util.JodaArgumentFactory;
import com.hello.suripu.core.db.util.PostgresIntegerArrayArgumentFactory;
import com.hello.suripu.coredw8.clients.AmazonDynamoDBClientFactory;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.db.ManagedDataSource;
import io.dropwizard.jdbi.ImmutableListContainerFactory;
import io.dropwizard.jdbi.ImmutableSetContainerFactory;
import io.dropwizard.jdbi.OptionalContainerFactory;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.skife.jdbi.v2.DBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.TimeZone;

public class PopulateSleepScoreParametersDynamoDBTable extends ConfiguredCommand<SuripuAppConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(PopulateSleepScoreParametersDynamoDBTable.class);



    public PopulateSleepScoreParametersDynamoDBTable() {
        super("populate_sleep_score_parameters", "populate prod_sleep_score_parameters table with personalized values");
    }

    @Override
    public void configure(Subparser subparser) {
        super.configure(subparser);

        subparser.addArgument("--test")
                .nargs("?")
                .required(false)
                .help("test args");

    }

    @Override
    protected void run(Bootstrap<SuripuAppConfiguration> bootstrap,
                       Namespace namespace,
                       SuripuAppConfiguration suripuAppConfiguration) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        // if you needed some additional arguments
        final String test = namespace.getString("test");

        // RDS
        final ManagedDataSource dataSource = (suripuAppConfiguration.getCommonDB().build(bootstrap.getMetricRegistry(), "commonDB"));
        final DBI jdbi = new DBI(dataSource);

        jdbi.registerArgumentFactory(new JodaArgumentFactory());
        jdbi.registerContainerFactory(new OptionalContainerFactory());
        jdbi.registerArgumentFactory(new PostgresIntegerArrayArgumentFactory());
        jdbi.registerContainerFactory(new ImmutableListContainerFactory());
        jdbi.registerContainerFactory(new ImmutableSetContainerFactory());

        final AccountDAO accountDAO = jdbi.onDemand(AccountDAOImpl.class);
        final QuestionResponseDAO questionResponseDAO = jdbi.onDemand(QuestionResponseDAO.class);

        // instantiate more DAOs here if needed

        // DynamoDB
        final ImmutableMap<DynamoDBTableName, String> tableNames = suripuAppConfiguration.dynamoDBConfiguration().tables();
        final AWSCredentialsProvider awsCredentialsProvider= new DefaultAWSCredentialsProviderChain();

        final ClientConfiguration clientConfiguration = new ClientConfiguration();
        clientConfiguration.withConnectionTimeout(200); // in ms
        clientConfiguration.withMaxErrorRetry(1);

        final AmazonDynamoDBClientFactory dynamoDBClientFactory = AmazonDynamoDBClientFactory.create(awsCredentialsProvider, new ClientConfiguration(), suripuAppConfiguration.dynamoDBConfiguration());

        final AmazonDynamoDB sleepStatsDAOClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.SLEEP_STATS);
        final SleepStatsDAODynamoDB sleepStatsDAODynamoDB = new SleepStatsDAODynamoDB(sleepStatsDAOClient,
                tableNames.get(DynamoDBTableName.SLEEP_STATS),
                suripuAppConfiguration.getSleepStatsVersion());

        final AmazonDynamoDB sleepScoreParametersClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.SLEEP_SCORE_PARAMETERS);
        final SleepScoreParametersDynamoDB sleepScoreParametersDynamoDB = new SleepScoreParametersDynamoDB(sleepScoreParametersClient, tableNames.get(DynamoDBTableName.SLEEP_SCORE_PARAMETERS));

        // compute custom parameters
    }
}
