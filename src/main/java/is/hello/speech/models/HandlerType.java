package is.hello.speech.models;

/**
 * Created by ksg on 6/17/16
 */
public enum HandlerType {
    NONE("none"),
    SLEEP_SOUNDS("sleep_sounds"),
    ALARM("alarm"),
    HUE("hue"),
    NEST("nest"),
    ROOM_CONDITIONS("room_conditions"),
    TIME_REPORT("time_report"),
    TIMELINE("timeline"),
    TRIVIA("trivia"),
    WEATHER("weather"),
    ALEXA("alexa"),
    SLEEP_SUMMARY("sleep_summary");

    public String value;

    HandlerType(String value) { this.value = value; }
}
