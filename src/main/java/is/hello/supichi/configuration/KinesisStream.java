package is.hello.supichi.configuration;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Created by ksg on 8/11/16
 */
public enum KinesisStream {
    AUDIO("audio"),
    SPEECH_RESULT("speech_result");

    private String value;

    KinesisStream(String text) { value = text; }
    public String value() { return value; }

    @JsonCreator
    public KinesisStream fromString(final String val) {
        return KinesisStream.getFromString(val);
    }

    public static KinesisStream getFromString(final String val) {
        for (final KinesisStream name:  KinesisStream.values()) {
            if (name.value.equalsIgnoreCase(val)) {
                return name;
            }
        }

        throw new IllegalArgumentException(String.format("%s is not a valid Speech KinesisStreamName", val));
    }
}