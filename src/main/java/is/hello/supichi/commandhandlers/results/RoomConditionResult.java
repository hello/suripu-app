package is.hello.supichi.commandhandlers.results;

/**
 * Created by ksg on 9/21/16
 */
public class RoomConditionResult {

    public final String sensorName;
    public final String sensorValue;
    public final String sensorUnit;


    public RoomConditionResult(final String sensorName, final String sensorValue, final String sensorUnit) {
        this.sensorName = sensorName;
        this.sensorValue = sensorValue;
        this.sensorUnit = sensorUnit;
    }
}
