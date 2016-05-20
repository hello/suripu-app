package com.hello.suripu.app.filters;

import com.hello.suripu.app.utils.AppFeatureFlipper;
import com.hello.suripu.core.util.RequestRateLimiter;
import com.librato.rollout.RolloutClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import java.io.IOException;
import java.util.Collections;

/**
 * Created by jakepiccolo on 5/11/16.
 */
public class RateLimitingByIPFilter implements ContainerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitingByIPFilter.class);

    private final RequestRateLimiter<String> ipRateLimiter;
    private final int tokensPerRequest;
    private final RolloutClient rolloutClient;

    public RateLimitingByIPFilter(RequestRateLimiter<String> ipRateLimiter, final int tokensPerRequest, final RolloutClient rolloutClient) {
        this.ipRateLimiter = ipRateLimiter;
        this.tokensPerRequest = tokensPerRequest;
        this.rolloutClient = rolloutClient;
    }

    protected static String getIpAddress(final ContainerRequestContext requestContext) {
        final String ipAddress = requestContext.getHeaderString("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty()) {
            return "";
        }

        final String[] ipAddresses = ipAddress.split(",");
        return ipAddresses[0]; // always return first one?
    }

    @Override
    public void filter(final ContainerRequestContext requestContext) throws IOException {
        final String ipAddress = getIpAddress(requestContext);

        final boolean whiteListed = rolloutClient.deviceFeatureActive(AppFeatureFlipper.DO_NOT_THROTTLE_IP.featureName,
                ipAddress,
                Collections.<String>emptyList());

        // Check if they exceeded the limit, and if they're not on the whitelist.
        if ( !(whiteListed || ipRateLimiter.canProceed(ipAddress, tokensPerRequest)) ) {
            LOGGER.warn("rate-limit-ip={} user-agent={} path={}",
                    ipAddress,
                    requestContext.getHeaderString("User-Agent"),
                    requestContext.getUriInfo().getAbsolutePath().getPath());
            throw new WebApplicationException(429);
        }
    }
}
