package com.hello.suripu.app.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import io.dropwizard.Configuration;

public class ExpansionConfiguration extends Configuration {

    @Valid
    @NotNull
    @JsonProperty("hue_app_name")
    private String hueAppName;
    public String hueAppName() {
        return hueAppName;
    }

}
