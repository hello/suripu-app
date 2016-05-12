package com.hello.suripu.app.filters;

import com.hello.suripu.core.util.RequestRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import java.io.IOException;

/**
 * Created by jakepiccolo on 5/11/16.
 */
public class RateLimitingByIPFilter implements ContainerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitingByIPFilter.class);

    private final RequestRateLimiter<String> ipRateLimiter;
    private final int tokensPerRequest;

    public RateLimitingByIPFilter(RequestRateLimiter<String> ipRateLimiter, final int tokensPerRequest) {
        this.ipRateLimiter = ipRateLimiter;
        this.tokensPerRequest = tokensPerRequest;
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
        if (!ipRateLimiter.canProceed(ipAddress, tokensPerRequest)) {
            // For now just log it
            LOGGER.warn("rate-limit-ip={} user-agent={} path={}",
                    ipAddress,
                    requestContext.getHeaderString("User-Agent"),
                    requestContext.getUriInfo().getAbsolutePath().getPath());
        }
    }
}
