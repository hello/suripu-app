package com.hello.suripu.app.v3;

import com.google.common.base.Optional;
import com.hello.suripu.app.alerts.AlertsProcessor;
import com.hello.suripu.app.v2.SleepSoundsResourceTest;
import com.hello.suripu.core.alerts.Alert;
import com.hello.suripu.core.alerts.AlertCategory;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.coredropwizard.oauth.AccessToken;
import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static junit.framework.TestCase.assertFalse;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Created by simonchen on 4/25/17.
 */
public class AlertsResourceTest {

    private static final Long MOCK_ACCOUNT_ID = 0L;

    private AccessToken accessToken;
    private com.hello.suripu.app.v3.AlertsResource alertsResource;
    private AlertsProcessor mockAlertsProcessor;

    @Before
    public void setUp() {
        accessToken = makeToken(MOCK_ACCOUNT_ID);
        mockAlertsProcessor = mock(AlertsProcessor.class);
        this.alertsResource = new com.hello.suripu.app.v3.AlertsResource(mockAlertsProcessor);
    }

    @Test
    public void getSenseAlert() throws Exception {
        doReturn(getMockAlertOptional(AlertCategory.SENSE_NOT_PAIRED))
                .when(mockAlertsProcessor)
                .getSenseAlertOptional(MOCK_ACCOUNT_ID);

        final List<Alert> alerts = alertsResource.get(accessToken);

        assertFalse(alerts.isEmpty());
        verify(mockAlertsProcessor, times(0)).getExistingAlertOptional(MOCK_ACCOUNT_ID);
        verify(mockAlertsProcessor, times(1)).getSenseAlertOptional(MOCK_ACCOUNT_ID);
        verify(mockAlertsProcessor, times(0)).getSystemAlertOptional(MOCK_ACCOUNT_ID);
        verify(mockAlertsProcessor, times(0)).getPillAlertOptional(MOCK_ACCOUNT_ID);
    }

    @Test
    public void getSystemAlert() throws Exception {
        doReturn(Optional.absent())
                .when(mockAlertsProcessor)
                .getSenseAlertOptional(MOCK_ACCOUNT_ID);

        doReturn(getMockAlertOptional(AlertCategory.EXPANSION_UNREACHABLE))
                .when(mockAlertsProcessor)
                .getSystemAlertOptional(MOCK_ACCOUNT_ID);

        final List<Alert> alerts = alertsResource.get(accessToken);

        assertFalse(alerts.isEmpty());
        verify(mockAlertsProcessor, times(0)).getExistingAlertOptional(MOCK_ACCOUNT_ID);
        verify(mockAlertsProcessor, times(1)).getSenseAlertOptional(MOCK_ACCOUNT_ID);
        verify(mockAlertsProcessor, times(1)).getSystemAlertOptional(MOCK_ACCOUNT_ID);
        verify(mockAlertsProcessor, times(0)).getPillAlertOptional(MOCK_ACCOUNT_ID);
    }

    @Test
    public void getSleepPillAlert() throws Exception {
        doReturn(Optional.absent())
                .when(mockAlertsProcessor)
                .getSenseAlertOptional(MOCK_ACCOUNT_ID);

        doReturn(Optional.absent())
                .when(mockAlertsProcessor)
                .getSystemAlertOptional(MOCK_ACCOUNT_ID);

        doReturn(getMockAlertOptional(AlertCategory.SLEEP_PILL_NOT_PAIRED))
                .when(mockAlertsProcessor)
                .getPillAlertOptional(MOCK_ACCOUNT_ID);

        final List<Alert> alerts = alertsResource.get(accessToken);

        assertFalse(alerts.isEmpty());
        verify(mockAlertsProcessor, times(0)).getExistingAlertOptional(MOCK_ACCOUNT_ID);
        verify(mockAlertsProcessor, times(1)).getSenseAlertOptional(MOCK_ACCOUNT_ID);
        verify(mockAlertsProcessor, times(1)).getSystemAlertOptional(MOCK_ACCOUNT_ID);
        verify(mockAlertsProcessor, times(1)).getPillAlertOptional(MOCK_ACCOUNT_ID);
    }

    private static Optional<com.hello.suripu.core.alerts.Alert> getMockAlertOptional(@NotNull final AlertCategory alertCategory) {
        final Alert mock = mock(Alert.class);
        doReturn(alertCategory).when(mock).category();
        return Optional.of(mock);
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
                .withRefreshToken(java.util.UUID.randomUUID())
                .withToken(java.util.UUID.randomUUID())
                .withScopes(new OAuthScope[]{
                        OAuthScope.USER_BASIC,
                        OAuthScope.ALERTS_READ })
                .withAppId(1L)
                .build();
    }
}