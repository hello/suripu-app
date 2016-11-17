package is.hello.supichi.models;

import is.hello.supichi.commandhandlers.AlarmHandler;
import is.hello.supichi.commandhandlers.AlexaHandler;
import is.hello.supichi.commandhandlers.HueHandler;
import is.hello.supichi.commandhandlers.NestHandler;
import is.hello.supichi.commandhandlers.RoomConditionsHandler;
import is.hello.supichi.commandhandlers.SleepSoundHandler;
import is.hello.supichi.commandhandlers.SleepSummaryHandler;
import is.hello.supichi.commandhandlers.TimeHandler;
import is.hello.supichi.commandhandlers.TimelineHandler;
import is.hello.supichi.commandhandlers.TriviaHandler;
import is.hello.supichi.commandhandlers.WaitHandler;
import is.hello.supichi.commandhandlers.WeatherHandler;

/**
 * Created by ksg on 6/21/16
 */
public enum SpeechCommand {
    SLEEP_SOUND_PLAY("sleep_sound_play", SleepSoundHandler.class),
    SLEEP_SOUND_STOP("sleep_sound_stop", SleepSoundHandler.class),
    ALARM_SET("alarm_set", AlarmHandler.class),
    ALARM_DELETE("alarm_delete", AlarmHandler.class),
    LIGHT_SET_BRIGHTNESS("light_set_brightness", HueHandler.class),
    LIGHT_SET_COLOR("light_set_color", HueHandler.class),
    LIGHT_TOGGLE("light_toggle", HueHandler.class),
    ROOM_CONDITION("room_condition", RoomConditionsHandler.class),
    ROOM_TEMPERATURE("room_temperature", RoomConditionsHandler.class),
    ROOM_HUMIDITY("room_humidity", RoomConditionsHandler.class),
    ROOM_LIGHT("room_light", RoomConditionsHandler.class),
    ROOM_SOUND("room_sound", RoomConditionsHandler.class),
    PARTICULATES("particulates", RoomConditionsHandler.class),
    THERMOSTAT_READ("thermostat_read", NestHandler.class),
    THERMOSTAT_SET("thermostat_set", NestHandler.class),
    THERMOSTAT_ACTIVE("thermostat_active", NestHandler.class),
    TIME_REPORT("time_report",TimeHandler.class),
    TRIVIA("trivia", TriviaHandler.class),
    WEATHER("weather", WeatherHandler.class),
    TIMELINE("timeline",TimelineHandler.class),
    ALEXA("alexa",AlexaHandler.class),
    SLEEP_SCORE("sleep_score", SleepSummaryHandler.class),
    SLEEP_SUMMARY("sleep_summary", SleepSummaryHandler.class),
    WAIT("wait",WaitHandler.class);

    private String value;
    private Class commandClass;

    SpeechCommand(String value, Class commandClass) {
        this.value = value;
        this.commandClass = commandClass;
    }

    public String getValue() { return value; }
    public Class getCommandClass() { return commandClass; }
}