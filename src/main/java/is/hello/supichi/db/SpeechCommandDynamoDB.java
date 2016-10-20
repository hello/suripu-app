package is.hello.supichi.db;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.CreateTableResult;
import com.hello.suripu.core.db.dynamo.Attribute;
import com.hello.suripu.core.db.dynamo.Util;
import is.hello.supichi.models.HandlerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


/**
 * Created by ksg on 6/20/16
 */
public class SpeechCommandDynamoDB implements SpeechCommandDAO{
    private static final Logger LOGGER = LoggerFactory.getLogger(SpeechCommandDynamoDB.class);

    private final AmazonDynamoDB dynamoDBClient;
    private final String tableName;

    public enum SpeechCommandAttribute implements Attribute {
        HANDLER("handler", "S"), // hash key
        TEXT("text", "S"), // range
        ACTION("action", "S");

        private final String name;
        private final String type;

        SpeechCommandAttribute(String name, String type) {
            this.name = name;
            this.type = type;
        }

        @Override
        public String shortName() {
            return name;
        }

        @Override
        public String sanitizedName() {
            return toString();
        }

        @Override
        public String type() {
            return type;
        }

    }

    public SpeechCommandDynamoDB(final AmazonDynamoDB dynamoDBClient, final String tableName) {
        this.dynamoDBClient = dynamoDBClient;
        this.tableName = tableName;
    }

    public CreateTableResult createTable(final Long readCapacity, final Long writeCapacity) {
        return Util.createTable(this.dynamoDBClient, this.tableName, SpeechCommandAttribute.HANDLER, readCapacity, writeCapacity);
    }

//    @Override
//    public Map<String, Action> getActionCommands(HandlerType handlerType) {
//        return null;
//    }

    @Override
    public List<String> getHandlerCommands(final HandlerType handlerType) {
        return null;
    }


}
