package com.hello.suripu.app.sharing;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.PrimaryKey;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class ShareDAODynamoDB implements ShareDAO {

    private final static Logger LOGGER = LoggerFactory.getLogger(ShareDAODynamoDB.class);

    private enum AttributeNames {
        UUID("uuid"),
        SHARE_TYPE("share_type");

        private String name;
        AttributeNames(String name) {
            this.name = name;
        }
    }

    private final Table table;

    private ShareDAODynamoDB(final Table table) {
        this.table = table;
    }


    public static ShareDAODynamoDB create(final Table table) {
        return new ShareDAODynamoDB(table);
    }

    @Override
    public String put(final Share share) {
        final UUID uuid = UUID.randomUUID();
        final String cleanUUID = uuid.toString().replace("-","");

        final Item item = new Item()
                .withPrimaryKey(new PrimaryKey(AttributeNames.UUID.name, cleanUUID))
                .withString(AttributeNames.SHARE_TYPE.name, share.type())
                .withString("payload", share.payload());
        table.putItem(item);
        LOGGER.info("action=share uuid={}", cleanUUID);
        return cleanUUID;
    }

    @Override
    public void delete(final String shareId) {
        table.deleteItem(new PrimaryKey(AttributeNames.UUID.name, shareId));
    }

    public static void createTableAndWait(final AmazonDynamoDB amazonDynamoDB, final String tableName) {
        final DynamoDB dynamoDB = new DynamoDB(amazonDynamoDB);
        final Table table = dynamoDB.createTable(
                tableName,
                Lists.newArrayList(
                        new KeySchemaElement().withAttributeName(AttributeNames.UUID.name).withKeyType(KeyType.HASH)
                ),
                Lists.newArrayList(
                        new AttributeDefinition().withAttributeName(AttributeNames.UUID.name).withAttributeType(ScalarAttributeType.S)
                ),
                new ProvisionedThroughput()
                        .withReadCapacityUnits(1L)
                        .withWriteCapacityUnits(1L)
        );
        try {
            table.waitForActive();
        } catch (InterruptedException e) {
            LOGGER.error("error=create-table-failed message={}", e.getMessage());
        }

    }
}
