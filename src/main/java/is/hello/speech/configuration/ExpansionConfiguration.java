package is.hello.speech.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class ExpansionConfiguration extends Configuration {

    @Valid
    @NotNull
    @JsonProperty("hue_app_name")
    private String hueAppName;
    public String hueAppName() {
        return hueAppName;
    }

}
