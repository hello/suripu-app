package is.hello.supichi.commandhandlers.results;

import com.hello.suripu.core.roomstate.Condition;

/**
 * Created by ksg on 9/21/16
 */
public class RoomConditionResult {

    public final String sensorName;
    public final String sensorValue;
    public final String sensorUnit;
    public final Condition condition;


    public RoomConditionResult(final String sensorName, final String sensorValue, final String sensorUnit, final Condition condition) {
        this.sensorName = sensorName;
        this.sensorValue = sensorValue;
        this.sensorUnit = sensorUnit;
        this.condition = condition;
    }
}
