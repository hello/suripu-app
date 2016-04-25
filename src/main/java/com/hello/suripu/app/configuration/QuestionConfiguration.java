package com.hello.suripu.app.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import io.dropwizard.Configuration;

public class QuestionConfiguration extends Configuration {

    @Valid
    @NotNull
    @JsonProperty("num_skips")
    private int numSkips;

    public int getNumSkips(){
        return numSkips;
    }

}
