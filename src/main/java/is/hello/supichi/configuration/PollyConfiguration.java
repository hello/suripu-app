package is.hello.supichi.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * Created by ksg on 6/28/16
 */
public class PollyConfiguration {
    @Valid
    @NotNull
    @JsonProperty("endpoint")
    private String endpoint;
    public String endpoint() { return endpoint; }

    @Valid
    @NotNull
    @JsonProperty("region")
    private String region;
    public String region() { return region; }


    @Valid
    @NotNull
    @JsonProperty("output_format")
    private String outputFormat;
    public String outputFormat() { return outputFormat; }

    @Valid
    @NotNull
    @JsonProperty("sample_rate")
    private String sampleRate;
    public String sampleRate() { return sampleRate; }

    @Valid
    @NotNull
    @JsonProperty("voice_id")
    private String voiceId;
    public String voiceId() { return voiceId; }

}