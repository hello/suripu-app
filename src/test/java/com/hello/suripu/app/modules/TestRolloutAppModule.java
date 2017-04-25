package com.hello.suripu.app.modules;

import com.hello.suripu.app.v2.AlertsResource;
import com.hello.suripu.app.v2.AlertsResourceTest;
import com.hello.suripu.core.actions.ActionProcessor;
import com.hello.suripu.core.actions.ActionProcessorFirehose;
import com.hello.suripu.core.analytics.AnalyticsTracker;
import com.librato.rollout.RolloutAdapter;
import com.librato.rollout.RolloutClient;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;

import static org.mockito.Mockito.mock;

@Module(library = true,
        overrides = true,
        injects = {
                AlertsResource.class,
                AlertsResourceTest.class,
        }
)
public class TestRolloutAppModule {

    public TestRolloutAppModule() {
    }

    @Provides
    @Singleton
    RolloutAdapter providesTestRolloutAdapter() {
        return mock(RolloutAdapter.class);
    }

    @Provides
    @Singleton
    RolloutClient providesTestRolloutClient() {
        return mock(RolloutClient.class);
    }

    @Provides
    @Singleton
    ActionProcessor providesTestActionProcessor()  { return mock(ActionProcessorFirehose.class); }

    @Provides
    @Singleton
    AnalyticsTracker providesTestAnalyticsTracker() {return mock(AnalyticsTracker.class);}
}
