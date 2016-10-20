package is.hello.supichi.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * Created by ksg on 8/3/16
 */
public class WolframAlphaConfiguration {
    @Valid
    @NotNull
    @JsonProperty("app_id")
    private String appId;
    public String appId() { return appId; }

    @Valid
    @NotNull
    @JsonProperty("format")
    private String format;
    public String format() { return format; }
}
