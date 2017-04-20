package com.hello.suripu.app.v2;

import com.google.common.base.Optional;
import com.hello.suripu.app.utils.AlertsProcessor;
import com.hello.suripu.core.alerts.Alert;
import com.hello.suripu.core.alerts.AlertCategory;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.coredropwizard.oauth.AccessToken;
import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static junit.framework.TestCase.assertFalse;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

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
    public void getSenseNotPairedAlert() throws Exception {
        doReturn(getMockAlertOptional(AlertCategory.SENSE_NOT_PAIRED))
                .when(mockAlertsProcessor)
                .getSenseAlertOptional(MOCK_ACCOUNT_ID);

        final List<Alert> alerts = alertsResource.get(accessToken);

        assertFalse(alerts.isEmpty());
        assertThat(alerts.get(0).category(), equalTo(AlertCategory.SENSE_NOT_PAIRED));
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
        assertThat(alerts.get(0).category(), equalTo(AlertCategory.EXPANSION_UNREACHABLE));
    }

    @Test
    public void getSleepPillNotPairedAlert() throws Exception {
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
        assertThat(alerts.get(0).category(), equalTo(AlertCategory.SLEEP_PILL_NOT_PAIRED));
    }

    private static Optional<Alert> getMockAlertOptional(@NotNull final AlertCategory alertCategory) {
        final Alert mock = mock(Alert.class);
        doReturn(alertCategory).when(mock).category();
        return Optional.of(mock);
    }

    /**
     * todo originally from {@link SleepSoundsResourceTest}
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