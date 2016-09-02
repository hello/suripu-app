package com.hello.suripu.app.managed;

import com.segment.analytics.Analytics;
import io.dropwizard.lifecycle.Managed;

public class AnalyticsManaged implements Managed {
    private final Analytics analytics;

    public AnalyticsManaged(final Analytics analytics) {
        this.analytics = analytics;
    }

    @Override
    public void start() throws Exception {
        // DO nothing
    }

    @Override
    public void stop() throws Exception {
        analytics.shutdown();
    }
}
