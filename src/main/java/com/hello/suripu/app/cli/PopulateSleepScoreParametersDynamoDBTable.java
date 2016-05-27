package com.hello.suripu.app.cli;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.base.Optional;
import com.hello.suripu.app.configuration.SuripuAppConfiguration;
import com.hello.suripu.core.configuration.DynamoDBTableName;
import com.hello.suripu.core.db.*;
import com.hello.suripu.core.db.util.JodaArgumentFactory;
import com.hello.suripu.core.db.util.PostgresIntegerArrayArgumentFactory;
import com.hello.suripu.core.models.AccountDate;
import com.hello.suripu.core.models.AggregateSleepStats;
import com.hello.suripu.core.models.SleepScoreParameters;
import com.hello.suripu.core.util.DateTimeUtil;
import com.hello.suripu.coredw8.clients.AmazonDynamoDBClientFactory;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.db.ManagedDataSource;
import io.dropwizard.jdbi.ImmutableListContainerFactory;
import io.dropwizard.jdbi.ImmutableSetContainerFactory;
import io.dropwizard.jdbi.OptionalContainerFactory;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.DBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.TimeZone;


public class PopulateSleepScoreParametersDynamoDBTable extends ConfiguredCommand<SuripuAppConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(PopulateSleepScoreParametersDynamoDBTable.class);
    public static final int MAX_NIGHT= 30;


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
        final QuestionResponseReadDAO questionResponseReadDAO = jdbi.onDemand(QuestionResponseReadDAO.class);

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
        final DateTime dateTime = DateTime.now(DateTimeZone.UTC);
        long accountIdTemp = 0L;
        String dateTemp;
        int nightCount = 0;
        long durationSum = 0;
        int idealDuration = 0;
        final ImmutableList<AccountDate> allAccountsDates = questionResponseReadDAO.getAccountDatebyResponse(69);

        //List of accountIds and Dates with 'great nights' ordered by account id, and date
        //loops through paired account ids and dates. when account id changes, calculates mean ideal duration for previous account id and inserts parameter

        for (final AccountDate accountDate : allAccountsDates) {
            //initializes accountId
            if (accountDate.accountId == 0) {
                accountIdTemp = accountDate.accountId;
            }
            //Inserts ideal duration and resets count
            else if (accountDate.accountId != accountIdTemp) {
                if (nightCount > 0 && durationSum > 0) {
                    idealDuration = (int) durationSum / nightCount;
                    SleepScoreParameters parameter = new SleepScoreParameters(accountIdTemp, dateTime, idealDuration);
                    sleepScoreParametersDynamoDB.upsertSleepScoreParameters(accountIdTemp, parameter);
                }

                nightCount = 0;
                durationSum = 0;
                idealDuration = 0;
                accountIdTemp = accountDate.accountId;
            }

            dateTemp =  DateTimeUtil.dateToYmdString(accountDate.created); //need to change datetime to string
            Optional<AggregateSleepStats> singleSleepStats = sleepStatsDAODynamoDB.getSingleStat(accountIdTemp, dateTemp);

            //Sums nights and Duration per accountId
            if (singleSleepStats.isPresent() && nightCount < MAX_NIGHT){
                nightCount += 1;
                durationSum += singleSleepStats.get().sleepStats.sleepDurationInMinutes;
            }
        }

    }
}
