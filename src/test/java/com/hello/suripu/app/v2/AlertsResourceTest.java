package com.hello.suripu.app.v2;

import com.google.common.base.Optional;
import com.hello.suripu.app.alerts.AlertsProcessor;
import com.hello.suripu.core.alerts.Alert;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.coredropwizard.oauth.AccessToken;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static junit.framework.TestCase.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class AlertsResourceTest {

    private static final Long MOCK_ACCOUNT_ID = 0L;

    private AccessToken accessToken;
    private AlertsResource alertsResource;
    private AlertsProcessor mockAlertsProcessor;

    @Before
    public void setUp() {
        accessToken = makeToken(MOCK_ACCOUNT_ID);
        mockAlertsProcessor = mock(AlertsProcessor.class);
        this.alertsResource = new AlertsResource(mockAlertsProcessor);
    }

    @Test
    public void getExistingAlert() throws Exception {
        doReturn(Optional.absent())
                .when(mockAlertsProcessor)
                .getExistingAlertOptional(MOCK_ACCOUNT_ID);

        final List<Alert> alerts = alertsResource.get(accessToken);
        assertTrue(alerts.isEmpty());
        verify(mockAlertsProcessor, times(1)).getExistingAlertOptional(MOCK_ACCOUNT_ID);
        verify(mockAlertsProcessor, times(0)).getSenseAlertOptional(MOCK_ACCOUNT_ID);
        verify(mockAlertsProcessor, times(0)).getSystemAlertOptional(MOCK_ACCOUNT_ID);
        verify(mockAlertsProcessor, times(0)).getPillAlertOptional(MOCK_ACCOUNT_ID);
    }

    /**
     * originally from {@link SleepSoundsResourceTest}
     */
    private static AccessToken makeToken(final Long accountId) {
        return new AccessToken.Builder()
                .withAccountId(accountId)
                .withCreatedAt(DateTime.now())
                .withExpiresIn(DateTime.now().plusHours(1).getMillis())
                .withRefreshExpiresIn(DateTime.now().plusHours(1).getMillis())
                .withRefreshToken(UUID.randomUUID())
                .withToken(UUID.randomUUID())
                .withScopes(new OAuthScope[]{
                        OAuthScope.USER_BASIC,
                        OAuthScope.ALERTS_READ })
                .withAppId(1L)
                .build();
    }

}