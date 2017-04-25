package com.hello.suripu.app.alerts;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.hello.suripu.core.alerts.Alert;
import com.hello.suripu.core.alerts.AlertCategory;
import com.hello.suripu.core.alerts.AlertsDAO;
import com.hello.suripu.core.db.AccountDAO;
import com.hello.suripu.core.models.Account;
import com.hello.suripu.core.models.device.v2.DeviceProcessor;
import com.hello.suripu.core.models.device.v2.Sense;
import com.hello.suripu.core.sense.metadata.HumanReadableHardwareVersion;
import com.hello.suripu.core.sense.voice.VoiceMetadata;
import com.hello.suripu.core.sense.voice.VoiceMetadataDAO;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * Created by simonchen on 4/19/17.
 */
public class AlertsProcessorTest {

    private static final Long MOCK_ACCOUNT_ID = 0L;

    private VoiceMetadataDAO mockVoiceMetadataDAO;
    private DeviceProcessor mockDeviceProcessor;
    private Account mockAccount;
    private AlertsProcessor alertsProcessor;
    private AccountDAO mockAccountDAO;
    private AlertsDAO mockAlertsDAO;

    @Before
    public void setUp() throws Exception {
        mockAccount = mock(Account.class);

        mockAlertsDAO = mock(AlertsDAO.class);

        doReturn(Optional.<Alert>absent())
                .when(mockAlertsDAO)
                .mostRecentNotSeen(MOCK_ACCOUNT_ID);

        mockVoiceMetadataDAO = mock(VoiceMetadataDAO.class);

        mockDeviceProcessor = mock(DeviceProcessor.class);

        mockAccountDAO = mock(AccountDAO.class);

        doReturn(Optional.of(mockAccount))
                .when(mockAccountDAO)
                .getById(MOCK_ACCOUNT_ID);

        alertsProcessor = new AlertsProcessor(
                mockAlertsDAO,
                mockVoiceMetadataDAO,
                mockDeviceProcessor,
                mockAccountDAO
        );
    }

    @Test
    public void getSenseMutedAlertFromExistingAlerts() throws Exception {
       mockSenseMuted();

       final Optional<Alert> existingAlert = alertsProcessor.getExistingAlertOptional(MOCK_ACCOUNT_ID);
        assertTrue(existingAlert.isPresent());
        assertThat(existingAlert.get().category(), equalTo(AlertCategory.SENSE_MUTED));
    }

    @Test
    public void getExpansionUnreachableAlertFromExistingAlerts() throws Exception {
        doReturn(ImmutableList.<Sense>builder().build())
                .when(mockDeviceProcessor)
                .getSenses(MOCK_ACCOUNT_ID);

        final Alert expansionUnreachable = mock(Alert.class);

        doReturn(AlertCategory.EXPANSION_UNREACHABLE)
                .when(expansionUnreachable).category();

        doReturn(Optional.of(expansionUnreachable))
                .when(mockAlertsDAO)
                .mostRecentNotSeen(anyLong());

        final Optional<Alert> existingAlert = alertsProcessor.getExistingAlertOptional(MOCK_ACCOUNT_ID);
        assertTrue(existingAlert.isPresent());
        assertThat(existingAlert.get().category(), equalTo(AlertCategory.EXPANSION_UNREACHABLE));
    }

    @Test
    public void getSenseNotPairedAlert() throws Exception {
        doReturn(ImmutableList.builder().build())
                .when(mockDeviceProcessor)
                .getSenses(MOCK_ACCOUNT_ID);

        final Optional<Alert> senseAlert = alertsProcessor.getSenseAlertOptional(MOCK_ACCOUNT_ID);

        assertTrue(senseAlert.isPresent());
        assertThat(senseAlert.get().category(), equalTo(AlertCategory.SENSE_NOT_PAIRED));
    }

    @Test
    public void getSenseMutedAlert() throws Exception {
        mockSenseMuted();

        final Optional<Alert> systemAlert = alertsProcessor.getSenseAlertOptional(MOCK_ACCOUNT_ID);

        assertTrue(systemAlert.isPresent());
        assertThat(systemAlert.get().category(), equalTo(AlertCategory.SENSE_MUTED));
    }

    @Test
    public void getSleepPillNotPairedAlert() throws Exception {
        doReturn(ImmutableList.builder().build())
                .when(mockDeviceProcessor)
                .getPills(MOCK_ACCOUNT_ID, mockAccount);

        final Optional<Alert> pillAlert = alertsProcessor.getPillAlertOptional(MOCK_ACCOUNT_ID);

        assertTrue(pillAlert.isPresent());
        assertThat(pillAlert.get().category(), equalTo(AlertCategory.SLEEP_PILL_NOT_PAIRED));
    }

    @Test(expected = AlertsProcessor.BadAlertRequestException.class)
    public void throwBadAlertRequestException() throws Exception {
        final Long INVALID_ACCOUNT_ID = 1L;
        doReturn(Optional.absent())
                .when(mockAccountDAO)
                .getById(INVALID_ACCOUNT_ID);

        alertsProcessor.getPillAlertOptional(INVALID_ACCOUNT_ID);
    }

    @Test(expected = AlertsProcessor.UnsupportedAlertCategoryException.class)
    public void throwUnsupportedAlertCategoryException() throws Exception {
        alertsProcessor.map(AlertCategory.EXPANSION_UNREACHABLE, MOCK_ACCOUNT_ID, DateTime.now());
    }

    private void mockSenseMuted() {
        final Sense mutedSense = mock(Sense.class);
        doReturn(HumanReadableHardwareVersion.SENSE_WITH_VOICE)
                .when(mutedSense)
                .hardwareVersion();

        VoiceMetadata mockVoiceMetadata = mock(VoiceMetadata.class);
        doReturn(true)
                .when(mockVoiceMetadata)
                .muted();

        doReturn(mockVoiceMetadata)
                .when(mockVoiceMetadataDAO)
                .get(any(), anyLong(), anyLong());

        doReturn(ImmutableList.<Sense>builder().add(mutedSense).build())
                .when(mockDeviceProcessor)
                .getSenses(MOCK_ACCOUNT_ID);

        doReturn(ImmutableList.builder().build())
                .when(mockDeviceProcessor)
                .getPills(MOCK_ACCOUNT_ID, mockAccount);
    }

}