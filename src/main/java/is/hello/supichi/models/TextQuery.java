package is.hello.supichi.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TextQuery {

    public final String senseId;
    public final String transcript;


    private TextQuery(String senseId, String transcript) {
        this.senseId = senseId;
        this.transcript = transcript;
    }

    @JsonCreator
    public static TextQuery create(
            @JsonProperty("sense_id") String senseId,
            @JsonProperty("transcript") String transcript) {
        return new TextQuery(senseId, transcript);
    }
}
