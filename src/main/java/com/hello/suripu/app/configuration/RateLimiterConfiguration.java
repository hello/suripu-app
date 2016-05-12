package com.hello.suripu.app.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;

import javax.validation.Valid;

/**
 * Created by jakepiccolo on 5/11/16.
 */
public class RateLimiterConfiguration extends Configuration {

    @Valid
    @JsonProperty("tokens_allowed_per_second")
    private Long tokensAllowedPerSecond = 10L;
    public Long getTokensAllowedPerSecond() { return tokensAllowedPerSecond; }

    @Valid
    @JsonProperty("max_ips_to_limit")
    private Integer maxIpsToLimit = 10000;
    public Integer getMaxIpsToLimit() { return maxIpsToLimit; }

}
