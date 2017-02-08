package com.hello.suripu.app.cli;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.google.common.collect.ImmutableMap;
import com.hello.suripu.app.configuration.SuripuAppConfiguration;
import com.hello.suripu.core.configuration.DynamoDBTableName;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.db.MergedUserInfoDynamoDB;
import com.hello.suripu.core.db.util.JodaArgumentFactory;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.util.PillColorUtil;
import com.hello.suripu.coredropwizard.clients.AmazonDynamoDBClientFactory;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.db.ManagedDataSource;
import io.dropwizard.jdbi.ImmutableListContainerFactory;
import io.dropwizard.jdbi.ImmutableSetContainerFactory;
import io.dropwizard.jdbi.OptionalContainerFactory;
import io.dropwizard.jdbi.args.OptionalArgumentFactory;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import org.skife.jdbi.v2.DBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * Created by pangwu on 1/9/15.
 */
public class RecreatePillColorCommand extends ConfiguredCommand<SuripuAppConfiguration> {
    private final static Logger LOGGER = LoggerFactory.getLogger(RecreatePillColorCommand.class);

    public RecreatePillColorCommand(){
        super("gen_colors", "Recreate pill colors from DeviceDAO");
    }

    @Override
    protected void run(Bootstrap<SuripuAppConfiguration> bootstrap, Namespace namespace, SuripuAppConfiguration configuration) throws Exception {
        final ManagedDataSource dataSource = (configuration.getCommonDB().build(bootstrap.getMetricRegistry(), "commonDB"));

        final DBI jdbi = new DBI(dataSource);
        jdbi.registerArgumentFactory(new OptionalArgumentFactory(configuration.getCommonDB().getDriverClass()));
        jdbi.registerContainerFactory(new ImmutableListContainerFactory());
        jdbi.registerContainerFactory(new ImmutableSetContainerFactory());
        jdbi.registerContainerFactory(new OptionalContainerFactory());
        jdbi.registerArgumentFactory(new JodaArgumentFactory());

        final DeviceDAO deviceDAO = jdbi.onDemand(DeviceDAO.class);

        final ImmutableMap<DynamoDBTableName, String> tableNames = configuration.dynamoDBConfiguration().tables();
        final AWSCredentialsProvider awsCredentialsProvider= new DefaultAWSCredentialsProviderChain();
        final AmazonDynamoDBClientFactory dynamoDBClientFactory = AmazonDynamoDBClientFactory.create(awsCredentialsProvider, configuration.dynamoDBConfiguration());

        final AmazonDynamoDB mergedInfoDynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.ALARM_INFO);
        final MergedUserInfoDynamoDB mergedUserInfoDynamoDB = new MergedUserInfoDynamoDB(mergedInfoDynamoDBClient, tableNames.get(DynamoDBTableName.ALARM_INFO));

        LOGGER.info("Getting all pills..");
        final List<DeviceAccountPair> activePills = deviceDAO.getAllPills(true);
        LOGGER.info("Get pills list completed");

        final HashMap<String, ArrayList<DeviceAccountPair>> sensePillMap = new HashMap<>();
        for(final DeviceAccountPair pill:activePills){

            LOGGER.info("Getting sense paired with account {}", pill.accountId);
            final List<DeviceAccountPair> sensePairedWithAccount = deviceDAO.getSensesForAccountId(pill.accountId);
            for(final DeviceAccountPair sense:sensePairedWithAccount){
                if(!sensePillMap.containsKey(sense.externalDeviceId)){
                    sensePillMap.put(sense.externalDeviceId, new ArrayList<DeviceAccountPair>());
                }
                sensePillMap.get(sense.externalDeviceId).add(pill);
            }
            LOGGER.info("{} sense has linked with account {}", sensePairedWithAccount.size(), pill.accountId);
        }

        for(final String senseId:sensePillMap.keySet()){

            final ArrayList<DeviceAccountPair> pillsLinkedToSense = sensePillMap.get(senseId);
            Collections.sort(pillsLinkedToSense, new Comparator<DeviceAccountPair>() {
                @Override
                public int compare(DeviceAccountPair o1, DeviceAccountPair o2) {
                    return Long.compare(o1.accountId, o2.accountId);
                }
            });

            LOGGER.info("Sense {} has {} accounts that has pill linked", senseId, pillsLinkedToSense.size());
            final HashMap<String, Color> pillIdToColorMap = new HashMap<>();
            final List<Color> colorList = PillColorUtil.getPillColors();
            for(final DeviceAccountPair pill:pillsLinkedToSense){
                Color pillColor;

                if(!pillIdToColorMap.containsKey(pill.externalDeviceId)) {
                    pillColor = colorList.get(pillIdToColorMap.size() % colorList.size());
                    pillIdToColorMap.put(pill.externalDeviceId, new Color(pillColor.getRGB()));
                }else{
                    pillColor = pillIdToColorMap.get(pill.externalDeviceId);
                }
                LOGGER.info("Pill {} linked with sense {} set to color {}", pill.externalDeviceId, senseId, pillColor.getRGB());
                mergedUserInfoDynamoDB.setPillColor(senseId, pill.accountId, pill.externalDeviceId, pillColor);
            }
        }

        LOGGER.info("Reprocess done!");
    }
}
