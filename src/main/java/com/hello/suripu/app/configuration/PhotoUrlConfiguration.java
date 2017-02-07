package com.hello.suripu.app.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * Created by david on 2/6/17.
 */
public class PhotoUrlConfiguration {

    public class Paths {
        @Valid
        @NotNull
        @JsonProperty("voice")
        private String voicePath;

        public String getVoicePath() {
            return this.voicePath;
        }
    }

    @Valid
    @NotNull
    @JsonProperty("endpoint")
    private String endpoint;

    public String getEndpoint() {
        return endpoint;
    }

    @Valid
    @NotNull
    @JsonProperty("paths")
    private Paths paths;

    public Paths getPaths() {
        return this.paths;
    }
}
