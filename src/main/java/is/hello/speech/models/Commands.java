package is.hello.speech.models;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.util.Map;

/**
 * Created by ksg on 6/28/16
 */
public class Commands {

    public enum Type {
        SET_ALARM("set_alarm"),
        DISMISS_ALARM("dismiss_alarm"),
        SLEEP_SOUNDS("sleep_sounds"),
        SLEEP_REPORT("sleep_report"),
        ROOM_CONDITIONS("room_conditions");

        protected String value;

        private Type(final String value) { this.value = value; }
    }

    private ImmutableMap<String, String> commandId2Text;


    public Commands() {
        commandId2Text = ImmutableMap.copyOf(populateCommandId2TextMap());

    }

    public ImmutableMap<String, String> getCommandId2Text() {
        return commandId2Text;
    }


    private static Map<String, String> populateCommandId2TextMap() {
        // TODO: read this from DB?
        final Map<String, String> tempMap = Maps.newTreeMap();
        int id = 1;

        String command = Type.SET_ALARM.value;
        tempMap.put(String.format("%s_%d", command, id++), "wake me up at [time]");
        tempMap.put(String.format("%s_%d", command, id++), "set alarm for [time] on [date]");

        command = Type.DISMISS_ALARM.value;
        tempMap.put(String.format("%s_%d", command, id++), "dismiss");

        command = Type.SLEEP_SOUNDS.value;
        tempMap.put(String.format("%s_%d", command, id++), "play sleep sounds");
        tempMap.put(String.format("%s_%d", command, id), "stop sleep sounds");

        return tempMap;
    }

}
